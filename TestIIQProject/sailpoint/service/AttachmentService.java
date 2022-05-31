// (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved.
package sailpoint.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attachment;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Rule;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.accessrequest.AccessRequest;

public class AttachmentService {


    private UserContext userContext;
    private Configuration systemConfig;

    private static final String ATTACHMENTS_ENABLED = "attachmentsEnabled";
    private static final String ATTACHMENTS_ALLOWED_TYPES = "attachmentsAllowedFileTypes";
    private static final String ATTACHMENTS_FILENAME_ALLOWED_SPECIAL_CHARACTERS = "attachmentsFilenameAllowedSpecialCharacters";
    private static final String ATTACHMENT_CONFIG_RULES = "attachmentConfigRules";

    private static final String BAD_FILE_TYPE = "attachments_bad_type";
    private static final String BAD_FILE_NAME = "attachments_bad_name";
    private static final String MISSING_FILE_NAME = "attachments_no_file_name";
    private static final String MISSING_EXT = "attachments_missing_ext";
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private static final Set<String> WINDOWS_RESERVED_FILENAMES = Stream.of("con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9").collect(Collectors.toSet());

    public AttachmentService(UserContext context) {
        this.userContext = context;
        this.systemConfig = Configuration.getSystemConfig();
    }

    /**
     * Returns true if attachments are enabled in the system config.
     * @return boolean
     */
    public boolean isAttachmentsEnabled() {
        return systemConfig.getBoolean(ATTACHMENTS_ENABLED);
    }

    /**
     * Validates an attachment filename prior to upload. Validation fails if the file type is not whitelisted, or
     * the filename is a Windows reserved name.
     * @param fileName Name of the file to validate
     * @return an empty String if there are no validation issues. If there are issues, an error message is returned.
     */
    public String validateFile(String fileName) throws GeneralException {
        if (Util.isNullOrEmpty(fileName)) {
            return new Message(MISSING_FILE_NAME).getLocalizedMessage();
        }

        // We expect a period in the filename before the extension. The file name can't end with a period.
        int extensionIndex = fileName.substring(0, fileName.length() -1).lastIndexOf(".");
        if (extensionIndex == -1) {
            return new Message(MISSING_EXT).getLocalizedMessage();
        }

        // Get the file type, without leading period.
        String fileType = fileName.substring(extensionIndex + 1).toLowerCase();
        Set<String> allowedTypes = getAllowedTypes();
        if (!allowedTypes.contains(fileType)) {
            return new Message(BAD_FILE_TYPE, allowedTypes.toString()).getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
        }

        // Get the file name prefix (the part before the extension period).
        String prefix = fileName.substring(0, extensionIndex).toLowerCase();
        if (WINDOWS_RESERVED_FILENAMES.contains(prefix)) {
            return new Message(BAD_FILE_NAME, prefix).getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
        }

        return "";
    }

    public int getMaxFileSize() {
        int limit = 20;
        int maxSize = Configuration.getSystemConfig().getInt(Configuration.ATTACHMENTS_MAX_FILE_SIZE);
        if (Configuration.getSystemConfig().getInt(Configuration.ATTACHMENTS_MAX_FILE_SIZE_LIMIT) > 0) {
            limit = Configuration.getSystemConfig().getInt(Configuration.ATTACHMENTS_MAX_FILE_SIZE_LIMIT);
        }
        if (maxSize > 0 && maxSize <= limit) {
            return maxSize;
        } else {
            return limit;
        }
    }

    /**
     * Truncate the file name if its too long. Dont truncate the file extension.
     *
     * @param fileName
     * @return String truncated file name
     */
    public String truncateFileName(String fileName) {
        // trim it out first
        String truncatedFileName = fileName.trim();

        if (truncatedFileName.length() > MAX_FILE_NAME_LENGTH) {
            // If there is an extension, truncate the prefix and tack the extension back on.
            int dotIndex = truncatedFileName.lastIndexOf('.');
            if (dotIndex > 0) {
                String prefix = truncatedFileName.substring(0, dotIndex);
                String ext = truncatedFileName.substring(dotIndex);
                truncatedFileName = prefix.substring(0, MAX_FILE_NAME_LENGTH - ext.length()) + ext;
            }

            // Otherwise just truncate the whole filename.
            else {
                truncatedFileName = truncatedFileName.substring(0, MAX_FILE_NAME_LENGTH);
            }
        }

        return truncatedFileName;
    }

    /**
     * Strips all characters except letters, numbers, and characters which have been whitelisted. Also strips leading
     * and trailing spaces and periods, and all other periods except for the last, which designates file type.
     * @param fileName the filename
     * @return String sanitized file name
     */
    public String sanitizeFileName(String fileName) throws GeneralException {
        if (Util.isNullOrEmpty(fileName)) {
            return "";
        }

        Set<Integer> whiteList = getCharacterWhiteList();

        // Strip all single characters which are not either alphanumeric or specifically whitelisted. Or dots. We deal
        // with dots later.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fileName.length(); i++) {
            int codepoint = fileName.codePointAt(i);
            if (Character.isLetterOrDigit(codepoint) || whiteList.contains(codepoint) || codepoint == 0x2e) {
                builder.appendCodePoint(codepoint);
            }
        }
        String strippedFileName = builder.toString();

        // Strip leading and trailing spaces and dots, and dots not part of the extension
        strippedFileName = StringUtils.strip(strippedFileName, " .");
        int extensionIndex = strippedFileName.lastIndexOf(".");
        if (extensionIndex != -1) {
            // At this point we don't care if the extension exists or what it is, we just don't want to strip the last dot.
            strippedFileName = StringUtils.remove(strippedFileName.substring(0, extensionIndex), '.') + strippedFileName.substring(extensionIndex);
        }

        return strippedFileName;
    }

    private Set<Integer> getCharacterWhiteList() {
        String allowedCharacters = systemConfig.getString(ATTACHMENTS_FILENAME_ALLOWED_SPECIAL_CHARACTERS);
        Set<Integer> chars = allowedCharacters == null ? Collections.emptySet() :
                allowedCharacters.codePoints()
                        .boxed()
                        .collect(Collectors.toSet());
        return chars;
    }

    private Set<String> getAllowedTypes() throws GeneralException {
        String allowedTypesStr = systemConfig.getString(ATTACHMENTS_ALLOWED_TYPES);
        Set<String> types = new HashSet<>();

        if (!Util.isNullOrEmpty(allowedTypesStr) && !"null".equals(allowedTypesStr)) {
            types.addAll(JsonHelper.listFromJson(String.class, allowedTypesStr));
            types.removeAll(Collections.singleton(null));
        }
        return types;
    }

    /**
     * Saves a new attachment object to the database
     * @param fileName Name of the file to persist
     * @param uploader The Identity that uploaded the file
     * @param fileContent The content of the file to persist
     * @return AttachmentDTO representation of the saved file
     * @throws GeneralException
     */
    public AttachmentDTO createAttachment(String fileName, Identity uploader, byte[] fileContent)
            throws GeneralException {
        Attachment att = new Attachment(fileName, uploader);
        att.setContent(encrypt(fileContent, userContext.getContext()));
        userContext.getContext().startTransaction();
        userContext.getContext().saveObject(att);
        userContext.getContext().commitTransaction();
        return new AttachmentDTO(att);
    }

    /**
     * Saves the given description value to the attachment in the database.
     * @param attachment
     * @param description
     * @throws GeneralException
     */
    public void setDescription(Attachment attachment, String description) throws GeneralException {
        attachment.setDescription(description);
        userContext.getContext().startTransaction();
        userContext.getContext().saveObject(attachment);
        userContext.getContext().commitTransaction();
    }

    /**
     * Returns true if any attachments are required for the combinations of requestee, requester, item, action in the request.
     * @param accessRequest
     * @return
     * @throws GeneralException
     */
    public boolean requestRequiresAttachment(AccessRequest accessRequest) throws GeneralException {
        AccessRequestConfigService accessRequestConfigService = new AccessRequestConfigService(userContext.getContext());
        Map<String,List<AccessRequestConfigDTO>> configs = accessRequestConfigService.getConfigsForRequest(userContext.getLoggedInUser(),
                accessRequest, Rule.Type.AttachmentConfig);
        return accessRequestConfigService.configsRequire(configs);
    }

    /**
     * Encrypt the given byte array.
     * @param content byte array to encrypt
     * @param context SailPointContext
     * @return
     * @throws GeneralException
     */
    public static byte[] encrypt(byte[] content, SailPointContext context) throws GeneralException {
        String stringContent = Base64.encodeBytes(content);
        String encryptedContent = context.encrypt(stringContent);
        return encryptedContent.getBytes();
    }

    /**
     * Decrypt the given byte array.
     * @param content byte array to decrypt
     * @param context SailPointContext
     * @return
     * @throws GeneralException
     */
    public static byte[] decrypt(byte[] content, SailPointContext context) throws GeneralException {
        String encryptedContent = new String(content);
        return Base64.decode(context.decrypt(encryptedContent));
    }


}
