package cyclic.bsp;

import ch.epfl.scala.bsp4j.*;
import cyclic.lang.compiler.CompileTimeException;
import cyclic.lang.compiler.CompilerLauncher;
import cyclic.lang.compiler.configuration.ConfigurationException;
import cyclic.lang.compiler.configuration.CyclicProject;
import cyclic.lang.compiler.resolve.TypeNotFoundException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
			capabilities.setCanReload(true);
			return new InitializeBuildResult("Cyclic Compiler", "0.0.2", "2.0.0", capabilities);
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
		return supplyIfInitialized(() -> new WorkspaceBuildTargetsResult(List.of(Projects.targetFor(project))));
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
			// every SourceFolderDependency also counts as a sources folder, but that's not exposed compiler-side yet
			BuildTargetIdentifier projectId = Projects.idFor(project);
			String projectRoot = project.sourcePath.toAbsolutePath().toUri().toString();
			return new SourcesResult(List.of(
					new SourcesItem(projectId, List.of(
							new SourceItem(projectRoot, SourceItemKind.DIRECTORY, false)))));
		});
	}
	
	public CompletableFuture<InverseSourcesResult> buildTargetInverseSources(InverseSourcesParams params){
		return null;
	}
	
	public CompletableFuture<DependencySourcesResult> buildTargetDependencySources(DependencySourcesParams params){
		return null;
	}
	
	public CompletableFuture<ResourcesResult> buildTargetResources(ResourcesParams params){
		return null;
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
				return new CompileResult(StatusCode.ERROR);
			}
			client.onBuildTaskFinish(finishNow(
					taskId,
					"Compiled project " + project.name,
					TaskDataKind.COMPILE_REPORT,
					new CompileReport(target, 0, 0), // we bail on first error
					StatusCode.OK));
			return new CompileResult(StatusCode.OK);
		});
	}
	
	public CompletableFuture<TestResult> buildTargetTest(TestParams params){
		return null;
	}
	
	public CompletableFuture<RunResult> buildTargetRun(RunParams params){
		return null;
	}
	
	public CompletableFuture<CleanCacheResult> buildTargetCleanCache(CleanCacheParams params){
		return supplyIfInitialized(() -> new CleanCacheResult(null, true));
	}
	
	public CompletableFuture<DependencyModulesResult> buildTargetDependencyModules(DependencyModulesParams params){
		return null;
	}
	
	public CompletableFuture<JvmRunEnvironmentResult> jvmRunEnvironment(JvmRunEnvironmentParams params){
		return null;
	}
	
	public CompletableFuture<JvmTestEnvironmentResult> jvmTestEnvironment(JvmTestEnvironmentParams params){
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
	
	private TaskFinishParams finishNow(TaskId id, String message, String kind, Object data, StatusCode status){
		var params = new TaskFinishParams(id, status);
		params.setEventTime(System.currentTimeMillis());
		params.setMessage(message);
		params.setDataKind(kind);
		params.setData(data);
		return params;
	}
}