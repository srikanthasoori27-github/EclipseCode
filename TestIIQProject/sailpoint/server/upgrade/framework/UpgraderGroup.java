package sailpoint.server.upgrade.framework;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sailpoint.server.ImportCommand;
import sailpoint.tools.Util;

/**
 * UpgraderGroup
 * @author Jeff Upton <jeffupt@gmail.com>
 */
public class UpgraderGroup 
{
	private String _name;
	
	private List<ImportCommand> _commands = new ArrayList<ImportCommand>();
	
	public String getName()
	{
		return _name;
	}
	
	public boolean isSingleCommand()
	{
		return _commands.size() == 1;
	}
	
	public ImportCommand getSingleCommand()
	{
		assert(_commands.size() == 1);
		return _commands.get(0);
	}
	
	public List<ImportCommand> getCommands()
	{
		return _commands;
	}
	
	public static List<UpgraderGroup> getUpgraderGroups(List<ImportCommand> commands)
	{
		List<UpgraderGroup> result = new ArrayList<UpgraderGroup>();
		
		Set<ImportCommand> usedCommands = new HashSet<ImportCommand>();
		
		for (ImportCommand command : Util.safeIterable(commands)) {
			if (usedCommands.contains(command)) {
				continue;
			}
			
			UpgraderGroup group = new UpgraderGroup();
			group._commands.add(command);
			
			if (command.getGroup() != null) {
				group._name = command.getGroup();
				
				for (ImportCommand cmd : Util.safeIterable(commands)) {
					if (Util.nullSafeEq(command.getGroup(),  cmd.getGroup()) && cmd != command && !usedCommands.contains(cmd)) {
						group._commands.add(cmd);
					}
				}
			}
			
			result.add(group);
			usedCommands.addAll(group._commands);
		}
		
		return result;
	}
}
