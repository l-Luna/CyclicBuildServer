package cyclic.bsp;

import ch.epfl.scala.bsp4j.*;
import cyclic.lang.compiler.configuration.CyclicPackage;
import cyclic.lang.compiler.configuration.CyclicProject;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ParametersAreNonnullByDefault
public class Projects{
	
	@NotNull
	public static BuildTarget targetFor(CyclicProject project){
		// every workspace only has one build target & project
		BuildTargetIdentifier projectId = idFor(project);
		List<String> tags = List.of(BuildTargetTag.APPLICATION);
		List<String> languages = List.of(Ids.CYCLIC_LANGUAGE_ID);
		var capabilities = new BuildTargetCapabilities(true, false, false);
		
		// TODO: update when subprojects are supported
		var target = new BuildTarget(projectId, tags, languages, List.of(), capabilities);
		target.setBaseDirectory(project.root.toAbsolutePath().toUri().toString());
		target.setDisplayName(project.name);
		target.setDataKind(BuildTargetDataKind.JVM);
		target.setData(new JvmBuildTarget(null, String.valueOf(project.jdk)));
		return target;
	}
	
	@NotNull
	public static BuildTargetIdentifier idFor(CyclicProject project){
		return new BuildTargetIdentifier("cyclic/" + project.name.replace(" ", "-"));
	}
	
	@NotNull
	public static List<Path> dependencySources(CyclicProject project){
		List<Path> ret = new ArrayList<>();
		for(CyclicPackage dep : project.dependencies)
			if(dep.type.equals("sourceFolder") || dep.type.equals("jar")){
				String location = dep.location;
				Path path = project.pathFromRoot(location);
				ret.add(path);
			}
		return ret;
	}
	
	public static List<DependencyModule> dependencyModules(CyclicProject project){
		List<DependencyModule> ret = new ArrayList<>();
		for(CyclicPackage dep : project.dependencies)
			if(dep.type.equals("mavenJar")){
				var module = new DependencyModule(dep.name, dep.version);
				var split = dep.name.split(":");
				module.setDataKind("maven");
				module.setData(new MavenDependencyModule(split[0], split[1], dep.version, List.of()));
				ret.add(module);
			}
		return ret;
	}
}