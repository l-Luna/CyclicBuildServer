package cyclic.bsp;

import ch.epfl.scala.bsp4j.BuildTarget;
import ch.epfl.scala.bsp4j.BuildTargetCapabilities;
import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.BuildTargetTag;
import cyclic.lang.compiler.configuration.CyclicProject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Projects{
	
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
		return target;
	}
	
	@NotNull
	public static BuildTargetIdentifier idFor(CyclicProject project){
		return new BuildTargetIdentifier("cyclic/" + project.name);
	}
}