package cyclic.bsp;

import ch.epfl.scala.bsp4j.BuildClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class ServerLauncher{
	
	public static void main(String[] args) throws ExecutionException, InterruptedException, IOException{
		if(args.length < 2){
			System.err.println("""
					Usage: --server <path to project file>
					Or:    --setupConfig <path to project file> <command to run this>
					""");
			System.exit(1);
		}
		
		var path = Path.of(args[1]).toAbsolutePath();
		
		if(args[0].equals("--server")){
			var server = new CyclicBuildServer(path);
			var launcher = new Launcher.Builder<BuildClient>()
					.setOutput(System.out)
					.setInput(System.in)
					.setLocalService(server)
					.setRemoteInterface(BuildClient.class)
					.create();
			server.setClient(launcher.getRemoteProxy());
			launcher.startListening().get();
		}else if(args[0].equals("--setupConfig")){
			// create a BSP connector file
			var target = Path.of("."); // current directory
			String name = "Cyclic Build Server";
			String version = "1.0.0";
			String bspVersion = "2.0.0";
			List<String> argsList = new ArrayList<>(Arrays.asList(args[2].split(" ")));
			argsList.add("--server");
			argsList.add(path.toString());
			String json = """
					{
						"name": "%s",
						"version": "%s",
						"bspVersion": "%s",
						"languages": ["cyc"],
						"argv": %s
					}
					""".formatted(name, version, bspVersion, argsList.stream()
					.map(x -> "\"" + x + "\"")
					.collect(Collectors.joining(", ", "[", "]")));
			Files.writeString(target, json);
		}else
			System.err.println("Unknown command: " + args[0]);
	}
}