package cyclic.bsp;

import ch.epfl.scala.bsp4j.*;
import cyclic.lang.compiler.CompilerLauncher;
import cyclic.lang.compiler.configuration.ConfigurationException;
import cyclic.lang.compiler.configuration.CyclicProject;
import cyclic.lang.compiler.problems.CompileTimeException;
import cyclic.lang.compiler.problems.ProblemsHolder;
import cyclic.lang.compiler.resolve.TypeNotFoundException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static cyclic.bsp.Projects.*;
import static cyclic.bsp.Projects.idFor;

public class CyclicBuildServer implements BuildServer{
	
	private CyclicProject project;
	private final Path projectPath;
	
	private BuildClient client;
	boolean initialized = false;
	
	public CyclicBuildServer(Path path){
		this.projectPath = path;
		updateProject();
		if(project == null)
			throw new IllegalArgumentException("Invalid project: " + path);
	}
	
	private void updateProject(){
		CyclicProject parsed;
		try{
			parsed = CyclicProject.parse(projectPath);
		}catch(IOException e){
			throw new RuntimeException(e);
		}
		project = parsed;
	}
	
	public CompletableFuture<InitializeBuildResult> buildInitialize(InitializeBuildParams params){
		return CompletableFuture.supplyAsync(() -> {
			initialized = true;
			var capabilities = new BuildServerCapabilities();
			// we can compile cyclic, but not run/debug/test
			capabilities.setCompileProvider(new CompileProvider(List.of("cyc")));
			capabilities.setDependencyModulesProvider(true);
			capabilities.setDependencySourcesProvider(true);
			capabilities.setCanReload(true);
			return new InitializeBuildResult("Cyclic Compiler", "0.0.4", "2.1.0", capabilities);
		});
	}
	
	public void onBuildInitialized(){
	
	}
	
	public CompletableFuture<Object> buildShutdown(){
		return CompletableFuture.supplyAsync(() -> {
			initialized = false;
			return null;
		});
	}
	
	public void onBuildExit(){
		System.exit(0);
	}
	
	public CompletableFuture<WorkspaceBuildTargetsResult> workspaceBuildTargets(){
		return supplyIfInitialized(() -> new WorkspaceBuildTargetsResult(List.of(targetFor(project))));
	}
	
	public CompletableFuture<Object> workspaceReload(){
		return supplyIfInitialized(() -> {
			updateProject();
			return null;
		});
	}
	
	public CompletableFuture<SourcesResult> buildTargetSources(SourcesParams params){
		return supplyIfInitialized(() -> {
			// we still only support one project, so we can ignore the target specified by params
			String projectSources = project.sourcePath.toAbsolutePath().toUri().toString();
			List<SourceItem> sources = List.of(new SourceItem(projectSources, SourceItemKind.DIRECTORY, false));
			return new SourcesResult(List.of(new SourcesItem(idFor(project), sources)));
		});
	}
	
	public CompletableFuture<DependencySourcesResult> buildTargetDependencySources(DependencySourcesParams params){
		return supplyIfInitialized(() -> new DependencySourcesResult(List.of(new DependencySourcesItem(
				idFor(project),
				dependencySources(project)
						.stream()
						.map(x -> x.toAbsolutePath().toUri().toString())
						.toList())
		)));
	}
	
	public CompletableFuture<DependencyModulesResult> buildTargetDependencyModules(DependencyModulesParams params){
		return supplyIfInitialized(() -> new DependencyModulesResult(List.of(new DependencyModulesItem(
				idFor(project),
				dependencyModules(project))
		)));
	}
	
	public CompletableFuture<CompileResult> buildTargetCompile(CompileParams params){
		return supplyIfInitialized(() -> {
			TaskId taskId = new TaskId("compile:" + params.getOriginId());
			var target = params.getTargets().get(0);
			client.onBuildTaskStart(startNow(
					taskId,
					"Compiling " + project.name,
					TaskDataKind.COMPILE_TASK,
					new CompileTask(target)));
			try{
				CompilerLauncher.main("-p", projectPath.toString());
			}catch(CompileTimeException | ConfigurationException | TypeNotFoundException e){
				client.onBuildTaskFinish(finishNow(
						taskId,
						"Compile failed with error: " + e,
						TaskDataKind.COMPILE_REPORT,
						new CompileReport(target, 1, 0), // we bail on first error
						StatusCode.ERROR));
				ProblemsHolder.problems.clear();
				return new CompileResult(StatusCode.ERROR);
			}
			client.onBuildTaskFinish(finishNow(
					taskId,
					"Compiled project " + project.name,
					TaskDataKind.COMPILE_REPORT,
					new CompileReport(target, 0, ProblemsHolder.problems.size()), // we bail on first error
					StatusCode.OK));
			ProblemsHolder.problems.clear();
			return new CompileResult(StatusCode.OK);
		});
	}
	
	public CompletableFuture<CleanCacheResult> buildTargetCleanCache(CleanCacheParams params){
		return supplyIfInitialized(() -> new CleanCacheResult(null, true));
	}
	
	public CompletableFuture<InverseSourcesResult> buildTargetInverseSources(InverseSourcesParams params){
		return null;
	}
	
	public CompletableFuture<ResourcesResult> buildTargetResources(ResourcesParams params){
		return null;
	}
	
	public CompletableFuture<TestResult> buildTargetTest(TestParams params){
		return null;
	}
	
	public CompletableFuture<RunResult> buildTargetRun(RunParams params){
		return null;
	}
	
	public CompletableFuture<DebugSessionAddress> debugSessionStart(DebugSessionParams params){
		return null;
	}
	
	public void setClient(BuildClient client){
		this.client = client;
	}
	
	private <T> CompletableFuture<T> supplyIfInitialized(Supplier<T> supplier){
		if(initialized)
			return CompletableFuture.supplyAsync(supplier);
		else{
			return CompletableFuture.supplyAsync(() -> {
				// TODO: error with code -32002
				throw new IllegalStateException("Build server is not initialized");
			});
		}
	}
	
	private TaskStartParams startNow(TaskId id, String message, String kind, Object data){
		var params = new TaskStartParams(id);
		params.setEventTime(System.currentTimeMillis());
		params.setMessage(message);
		params.setDataKind(kind);
		params.setData(data);
		return params;
	}
	
	private TaskProgressParams progressNow(TaskId id, String message, String kind, Object data){
		var params = new TaskProgressParams(id);
		params.setEventTime(System.currentTimeMillis());
		params.setMessage(message);
		params.setDataKind(kind);
		params.setData(data);
		return params;
	}
	
	private TaskFinishParams finishNow(TaskId id, String message, String kind, Object data, StatusCode status){
		var params = new TaskFinishParams(id, status);
		params.setEventTime(System.currentTimeMillis());
		params.setMessage(message);
		params.setDataKind(kind);
		params.setData(data);
		return params;
	}
}