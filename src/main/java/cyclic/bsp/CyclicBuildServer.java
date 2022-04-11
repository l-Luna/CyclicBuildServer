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

public class CyclicBuildServer implements BuildServer, JvmBuildServer{
	
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
			capabilities.setCompileProvider(new CompileProvider(List.of("cyclic")));
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
		return null;
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
			try{
				CompilerLauncher.main("-p", projectPath.toString());
			}catch(CompileTimeException | ConfigurationException | TypeNotFoundException e){
				return new CompileResult(StatusCode.ERROR);
			}
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
		return null;
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
}