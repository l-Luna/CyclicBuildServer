package cyclic.bsp;

import ch.epfl.scala.bsp4j.*;
import cyclic.lang.compiler.configuration.CyclicProject;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
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
		return new BuildTargetIdentifier("cyclic/" + project.name);
	}
	
	@NotNull
	public static List<SourceItem> extraSourceFolders(CyclicProject project){
		return project.dependencies.stream()
				.filter(x -> x.type.equals("sourceFolder"))
				.map(x -> x.location)
				.map(x -> new SourceItem(project.pathFromRoot(x).toAbsolutePath().toUri().toString(), SourceItemKind.DIRECTORY, false))
				.toList();
	}
}