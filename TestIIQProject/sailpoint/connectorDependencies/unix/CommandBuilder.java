/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connectorDependencies.unix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import connector.common.Util;
import connector.common.logging.ConnectorLogger;
import openconnector.ConnectorConfig;
import openconnector.ConnectorException;
import openconnector.InvalidRequestException;
import openconnector.Item;

public class CommandBuilder {
    private static ConnectorLogger log = ConnectorLogger.getLogger(CommandBuilder.class);
    private Command m_commandObj;
    private String m_operationType;
    private ConnectorConfig m_config;
    private Map<String,Object> m_defaultcommandInfo = new HashMap<String,Object>();
    private Map<String,String> m_commandNameMap = new HashMap<String,String>();

    public final String INFO_FLAGS = "flags";
    public final String INFO_EXITSTS = "exitsts";
    public final String GROUP_IDENTITY_ATTRIBUTE = "Group Name";

    /**
     * restricted characters in the command argument
     * if found then it may be shell injection attack
     */
    public final String restrictedCharacters = ".*(;|&|\\|).*";

    // Dont use this constructor, added for Unix RW Target Collector.
    public CommandBuilder(String operationType, ConnectorConfig Connconfig, Log log, Map<String, Object> defaultInfo) {
        this(operationType, Connconfig, defaultInfo);
    }

    public CommandBuilder(String operationType, ConnectorConfig Connconfig, Map<String, Object> defaultInfo) {
        m_operationType = operationType;
        m_commandObj = new Command();
        m_config = Connconfig;
        if (defaultInfo != null) {
            m_commandNameMap = (Map<String, String>) defaultInfo.get("commandNames");
            m_defaultcommandInfo = (Map<String, Object>) defaultInfo.get("commandInfo");
        }
    }

    /**
     * get Command name from configuration list
     * 
     * */
    private String getCommandName(String operationType) {
        String commandName =null;
        Map<String,Object> attrList=null;
        attrList = m_config.getConfig();
        if (null!= attrList) {
            Object tmp =  attrList.get(operationType);
            if (tmp != null) {
                commandName = tmp.toString();
                log.debug(() -> "Command Name entry found in application object");
            } else {
                log.debug(() -> "Command Name entry not found in application object. Loading default value.");
                commandName = m_commandNameMap.get(operationType);
            }
        }
        final String finalCommandName = commandName;

        log.debug(() -> "Command Name being returned - "+ finalCommandName);

        return (commandName);
    }
    private Map<String,String> getDefaultCommandInfo(String cmd,String infoType){
        Map<String,String> commandInfo = null;
        Map<String,Object> commandInfoTypes = new HashMap<String,Object>();

        // Search and return requested command info.
        commandInfoTypes.clear();
        if (m_defaultcommandInfo != null)
        commandInfoTypes = (Map<String,Object>) m_defaultcommandInfo.get(cmd);
        if (commandInfoTypes != null)
            commandInfo = (Map<String,String>) commandInfoTypes.get(infoType);

        return commandInfo;
    }
    private Map<String,String> getCommandInfo(String cmd, String infoType) {
        Map<String,Object> attrMap=null;
        Map<String,Object> commandMap = null;
        Map<String,String> returnMap = null;

        attrMap = m_config.getConfig();

        if (attrMap != null) {
            commandMap = (Map<String,Object>) attrMap.get(cmd);
            if (commandMap != null) {
                log.debug(() -> "Command entry found in application object");
                returnMap = (Map<String,String>)commandMap.get(infoType);
            } else {
                log.debug(() -> "Command entry not found in application object. Loading default values.");
                // add logic to populate flagsMap with hard coded values from code - backup validation

                returnMap = getDefaultCommandInfo(cmd,infoType);
                if (returnMap == null) {
                    log.debug(()-> "Command entry not found in default values.");
                }
            }
        }

        return returnMap;
    }

    public Command buildCommand(){

        return buildCommand(null,null,null);

    }
    /**
     * Method to build command using flag map if required. Converts the provided
     * map into list of Item. Fixed as part of SF# 00048344
     * 
     * @param nativeIdentifier
     * @param options
     * @return
     */
    public Command buildCommand(String nativeIdentifier, Map<String, Object> options) {

        if (options != null) {
            List<Item> lstlItems = new ArrayList<Item>();

            for (Map.Entry<String, Object> entry : options.entrySet()) {
                lstlItems.add(new Item(entry.getKey(), entry.getValue()));
            }

            return buildCommand(nativeIdentifier, lstlItems, null);
        } else {
            return buildCommand(nativeIdentifier, null, null);
        }
    }

    public Command buildCommand(String nativeIdentifier,List<Item> items ){

        return buildCommand(nativeIdentifier,items,null);

    }

    public Command buildCommand(String nativeIdentifier){

        return buildCommand(nativeIdentifier,null,null);

    }

    /**
     * Build Command as per operation requested by user with appropriate command flags
     * @param nativeIdentifier
     * @param items
     * @param attribute
     * 
     * @throws ConnectorException
     * */
    public Command buildCommand( String nativeIdentifier, List<Item> items,String attribute) {
        // Adding nativeIdentifier in double quotes to avoid shell injection into nativeIdentifier 
        if (Util.isNotNullOrEmpty(nativeIdentifier)) {

            // if the nativeIdentifier has any backslash then we need to escape it
            if (nativeIdentifier.contains("\\")) {
                nativeIdentifier = nativeIdentifier.replace("\\", "\\\\");
            }

            // if the nativeIdentifier has any double quotes then we need to escape it
            if (nativeIdentifier.contains("\"")) {
                nativeIdentifier = nativeIdentifier.replace("\"", "\\\"");
            }

            // if the nativeIdentifier has any ` character then we need to escape it
            if (nativeIdentifier.contains("`")) {
                nativeIdentifier = nativeIdentifier.replace("`", "\\`");
            }

            // Put nativeIdentifier into double quotes to guard against shell injection
            nativeIdentifier = "\"" + nativeIdentifier + "\"";
        }
        String cmdAttrSeparator = " ";
        String cmd;
        final String blankString = "";
        String connectorCode = "";
        String tagValue = m_config.getString("ConnectorCode");
        if (tagValue != null) {
            connectorCode = tagValue;
        }
        m_commandObj.noOptAdded = true;
        ArrayList<String> groupList = new ArrayList<String>();

        String targetCommand = new String(blankString);
        cmd =  getCommandName(m_operationType);
        if (cmd == null) {
            throw new InvalidRequestException(
                    "No command definition found for operation " + m_operationType,
                    null,
                    null);
        }
        targetCommand += cmd;

        if (attribute != null) {
            targetCommand += cmdAttrSeparator;
            targetCommand += attribute;
        }

        Map<String, String>flagMap = getCommandInfo(targetCommand,INFO_FLAGS);
        boolean isPermission = false;
        if (m_operationType.compareTo("remove.account.permission") == 0 ||
            m_operationType.compareTo("remove.group.permission") == 0) {
            isPermission = true;
        }

        // To add correct password policy attributes for Solaris only
        if (m_operationType != null && m_operationType.equalsIgnoreCase("change.password") && items != null && flagMap != null) {
            for (Item item : items) {
                if (item.getName().equalsIgnoreCase("forcepwdchange") && !flagMap.containsKey("forcepwdchange")) {
                    flagMap.put("forcepwdchange", "-f");
                }
                if (item.getName().equalsIgnoreCase("pwdminage") && !flagMap.containsKey("pwdminage")) {
                    flagMap.put("pwdminage", "-n");
                }
                if (item.getName().equalsIgnoreCase("pwdmaxage") && !flagMap.containsKey("pwdmaxage")) {
                    flagMap.put("pwdmaxage", "-x");
                }
            }
        } // Ends

        boolean isInvalidArgument = false;

        // compare flagMap with item and create a final string to be added to command
        if ((flagMap != null && !flagMap.isEmpty()) && (items != null && !items.isEmpty())) {
            for (openconnector.Item item : items) {
                String finalval = blankString;
                String key;
                Object value;

                if (isPermission) {

                    // name contains file path, value contains permission.
                    value = item.getName();
                    key = (String)item.getValue();
                } else {
                    key = item.getName();
                    value = item.getValue();
                }

                // Convert input into String, as we are to add multiple values in string after respective keyword
                if (value != null && value instanceof ArrayList) {
                    int len =  value.toString().length();
                    finalval = value.toString().substring(1,len-1);
                } else if (value != null && (value instanceof String || value instanceof Boolean)) {
                    finalval = value.toString();
                }

                // if the finalval has any backslash then we need to escape it
                if (finalval.contains("\\")) {
                    finalval = finalval.replace("\\", "\\\\");
                }

                // if finalval contains " then we need to escape it
                if (finalval.contains("\"")) {
                    finalval = finalval.replace("\"", "\\\"");
                }

                // if the finalval has any ` character then we need to escape it
                if (finalval.contains("`")) {
                    finalval = finalval.replace("`", "\\`");
                }

                // Put data into double quotes to manage data having multiple words. 
                finalval = "\"" + finalval + "\"";

                String opt = "";
                if (isPermission && (key != null && key.contains(","))) {
                    /* If multiple permissions per resources then add them all in once command */
                    String[]keyOptions = key.split(",");
                    for (int i = 0; i < keyOptions.length; i++) {
                        opt += flagMap.get(keyOptions[i]);
                    }
                } else {
                    opt = flagMap.get(key);
                }

                if (opt != null) {
                    // check command argument for any shell injection attack
                    // scan for ; & | characters
                    if (!isInvalidArgument) {
                        isInvalidArgument = opt.matches(restrictedCharacters);
                    }

                    if (m_operationType.compareTo("create.account") == 0) {
                        if (key.equalsIgnoreCase("groups")) {
                            if (!groupList.contains(finalval)) {
                                groupList.add(finalval);
                            }
                            continue;
                        }
                        if ((connectorCode.equalsIgnoreCase("LINUX") && 
                           (opt.compareTo("-m") == 0) &&
                           (finalval.equals("\"false\"") == true))) {
                            opt = "-M";
                            m_commandObj.noOptAdded = false;
                            targetCommand += cmdAttrSeparator;
                            targetCommand += opt;
                            continue;
                        }
                    }
                    if (finalval.equalsIgnoreCase("\"true\"")) {
                        m_commandObj.noOptAdded = false;
                        targetCommand += cmdAttrSeparator;
                        targetCommand += opt;
                        continue;
                    } else if (finalval.equalsIgnoreCase("\"false\"")) {
                        continue;
                    }
                    if (opt.equalsIgnoreCase("-K")) {
                        m_commandObj.noOptAdded = false;
                        targetCommand += cmdAttrSeparator;
                        targetCommand += opt;
                        targetCommand += cmdAttrSeparator;
                        targetCommand += key;
                        targetCommand += "=";
                        targetCommand += finalval;
                        continue;
                    }

                    m_commandObj.noOptAdded = false;

                    // Don not add space incase of revoke permission operation
                    if (!isPermission) {
                        targetCommand += cmdAttrSeparator;
                    }
                    targetCommand += opt;
                    targetCommand += cmdAttrSeparator;
                    targetCommand += finalval;
                }
            } //end of for
        } else if (flagMap == null && items != null &&
             ((m_operationType.compareTo("create.account") == 0) ||
             (m_operationType.compareTo("modify.account") == 0) ||
             (m_operationType.compareTo("create.group") == 0) ||
             (m_operationType.compareTo("modify.group") == 0) )) {
             for (openconnector.Item item : items) {
                 String idName = item.getName();

                 /*Do not add password attribute in mksuer command for AIX connector*/
                 if ((m_operationType.compareTo("create.account") == 0) && idName.equals("*password*")) {
                     continue;
                 }

                 // If group name comes in item list, it should be ignored
                 if (((m_operationType.compareTo("create.group") == 0) ||
                     (m_operationType.compareTo("modify.group") == 0)) && 
                     idName.contentEquals(GROUP_IDENTITY_ATTRIBUTE)) {
                     continue;
                 }
                     // check command argument for any shell injection attack
                     // scan for ; & | characters
                     if (!isInvalidArgument) {
                         isInvalidArgument = idName.matches(restrictedCharacters);
                     }

                     if (item.getValue() != null) {
                     String idValue = item.getValue().toString().trim();
                     if (idValue != null) {
                         // if idValue contains any backslash then we need to escape it
                         if (idValue.contains("\\")) {
                             idValue = idValue.replace("\\", "\\\\");
                         }

                         // if idValue contains " then we need to escape it
                         if (idValue.contains("\"")) {
                             idValue = idValue.replace("\"", "\\\"");
                         }
                         
                         // if the idValue has any ` character then we need to escape it
                         if (idValue.contains("`")) {
                             idValue = idValue.replace("`", "\\`");
                         }
                         targetCommand += cmdAttrSeparator;
                         final String finalidValue = idValue;
                         log.debug(() -> idName + "=" + finalidValue );
                         if (idValue.matches("NULL")) {
                             targetCommand += idName + "=" + " ";
                         } else {
                             idValue = "\"" + idValue + "\"";
                             targetCommand += idName + "=" + idValue;
                         }
                     }
                 } else {
                     targetCommand += cmdAttrSeparator;
                     log.debug(() -> idName + "=NULL" );
                     targetCommand += idName + "=" + " ";
                 }
             }
         }

        if (!groupList.isEmpty()) {
            targetCommand+= cmdAttrSeparator;
            targetCommand+= flagMap.get("groups");
            targetCommand+= cmdAttrSeparator;
            String groupString = groupList.toString().replaceAll("\\s","");
            int len =  groupString.length();
            targetCommand += groupString.substring(1,len-1);
        }

        if (nativeIdentifier != null && !isPermission) {
            targetCommand += cmdAttrSeparator;
            targetCommand += nativeIdentifier;
        }

        if (isInvalidArgument) {
            throw new InvalidRequestException(
                    "Invalid argument found in the command: " + targetCommand,
                    null,
                    null);
        }

        // Validate command for any shell injection
        validateCommand(cmd, targetCommand, null, nativeIdentifier);

        final String finalTargetCommand = targetCommand;
        log.debug(() -> "Final command: " + finalTargetCommand);

        m_commandObj.setCommandString(targetCommand);
        m_commandObj.setExpResult(getCommandInfo(targetCommand, INFO_EXITSTS));

        return m_commandObj;
    }

    /**
     * This method analyzes the command for shell injection attack
     * @param cmd - it is command from application file e.g useradd
     * @param targetCommand - it is full target command e.g useradd "-c" "TestNG" "nativeIdentity"
     * @param defaultCommand - it is command from default commands map in the UnixConnector e.g useradd
     * @param nativeIdentifier
     * @throws InvalidRequestException
     */
    public void validateCommand(String cmd, String targetCommand, String defaultCommand, String nativeIdentifier) {
        if (defaultCommand == null) {
            defaultCommand = m_commandNameMap.get(m_operationType);
        }

        String cmdAttrSeparator = " ";

        // When target command and default command is same then no validation required
        // They will be same for account and group aggregation operations 
        if (targetCommand != null && defaultCommand != null && targetCommand.equals(defaultCommand)) {
            log.debug(() -> "Target command is same as default command, hence skipping command validation");
            return;
        }

        // when target command is combination of default command and native identifier
        // then no validation required
        if (targetCommand != null && defaultCommand != null && nativeIdentifier != null) {
            String command = defaultCommand + cmdAttrSeparator + nativeIdentifier;
            if (targetCommand.equals(command)) {
                log.debug(() -> "Target command is same as default command, hence skipping command validation");
                return;
            }
        }
        
        String harmfulCommands = "|chown |kill |chgrp |export |poweroff|halt|reboot|shutdown|yes|rmdir |shred ).*";

        String regex = ".*(rm |useradd|mkuser|groupadd|mkgroup|userdel|rmuser|groupdel|rmgroup" +
                "|chmod|usermod|chuser|groupmod|chgroup|passwd|chage" + harmfulCommands;

        String destructiveCommands = regex;
        String possibleSuggestion = null;

        // Check whether command is tampered 
        // e.g useradd is tampered like rm *; useradd
        if (cmd != null) {
            if (m_operationType != null) {
                if (m_operationType.equals("create.account")) {
                    // removed useradd and mkuser from finalRegex
                    destructiveCommands = regex.replaceAll("useradd\\|", "").replaceAll("mkuser\\|", "");
                } else if (m_operationType.equals("modify.account")) {
                    // removed usermod and chuser from finalRegex
                    destructiveCommands = regex.replaceAll("usermod\\|", "").replaceAll("chuser\\|", "");
                } else if (m_operationType.equals("delete.account")) {
                    // removed userdel and rmuser from finalRegex
                    destructiveCommands = regex.replaceAll("userdel\\|", "").replaceAll("rmuser\\|", "");
                } else if (m_operationType.equals("create.group")) {
                    // removed groupadd and mkgroup from finalRegex
                    destructiveCommands = regex.replaceAll("groupadd\\|", "").replaceAll("mkgroup\\|", "");
                } else if (m_operationType.equals("modify.group")) {
                    // removed groupmod and chgroup from finalRegex
                    destructiveCommands = regex.replaceAll("groupmod\\|", "").replaceAll("chgroup\\|", "");
                } else if (m_operationType.equals("delete.group")) {
                    // removed groupdel and rmgroup from finalRegex
                    destructiveCommands = regex.replaceAll("groupdel\\|", "").replaceAll("rmgroup\\|", "");
                } else if (m_operationType.equals("aggregation.account") 
                        || m_operationType.equals("change.password")
                        || m_operationType.equals("enable.account")
                        || m_operationType.equals("disable.account")
                        || m_operationType.equals("get.userpwdrow") // get.userpwdrow is for Solaris only
                        || m_operationType.equals("unlock.account")) {
                    // removed passwd, chuser, usermod and chage from finalRegex
                    // passwd -l do not fully disable account, user can login using key based authentication
                    // usermod -s /bin/false does disable account fully
                    // chage -E 0 also does disable account fully
                    destructiveCommands = regex.replaceAll("passwd\\|", "")
                            .replaceAll("chuser\\|", "")
                            .replaceAll("usermod\\|", "")
                            .replaceAll("chage\\|", "");
                } else if (m_operationType.equals("changepassword.resetmode")) {
                    // removed chage, passwd and chuser from finalRegex
                    destructiveCommands = regex.replaceAll("chage\\|", "").replaceAll("passwd\\|", "")
                                      .replaceAll("chuser\\|", "");
                } else if (m_operationType.equals("remove.account.permission") 
                        || m_operationType.equals("remove.group.permission")) {
                    // removed chmod from finalRegex
                    destructiveCommands = regex.replaceAll("chmod\\|", "");
                } else if (m_operationType.equals("remove.remotefile")) {
                    // removed rm from finalRegex
                    destructiveCommands = regex.replaceAll("rm \\|", "");
                }

                // Provide possible suggestion with attribute name
                possibleSuggestion = "Make sure the application configuration attribute '" + m_operationType + "' is set correctly.";
            }

            if (cmd.matches(destructiveCommands)) {
                InvalidRequestException e = new InvalidRequestException();
                e.setDetailedError("Shell injection attack detected in the command '" + cmd + "' .");
                e.setPossibleSuggestion(possibleSuggestion);

                throw e;
            }
        }
    }

    public Command getCommand(){
        return this.m_commandObj;
    }

    public void setCommand(Command newCommand){
        this.m_commandObj = newCommand;

    }
}
