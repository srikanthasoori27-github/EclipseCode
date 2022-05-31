// (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved.
package sailpoint.rest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import sailpoint.authorization.AttachmentsAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.*;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.service.AttachmentService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.service.AttachmentDTO;

@Path("attachment")
public class AttachmentResource extends BaseResource {

    private static final String ATTACHMENTS_DISABLED = "attachments_disabled";
    private static final String MISSING_FILE = "attachments_no_file";
    private static final String ATTACHMENT_NOT_FOUND = "attachments_not_found";
    private static final String ATTACHMENT_IN_USE = "attachments_in_use";
    private static final String DESCRIPTION_MISSING = "attachments_desc_missing";
    private static final String DESCRIPTION_TOO_LONG = "attachments_desc_too_long";
    private static final String DOWNLOAD_ERROR = "attachments_download_fail_content";
    private static final String FILE_TOO_BIG = "attachments_bad_size";

    /**
     * Get attachment with id exists and return it
     * @param attachmentId
     * @return Attachment
     * @throws Exception
     */
    private Attachment getAttachment(String attachmentId) throws GeneralException {
        if (Util.isNullOrEmpty(attachmentId)) {
            throw new InvalidParameterException("attachId");
        }

        Attachment attachment = getContext().getObjectById(Attachment.class, attachmentId);
        return attachment;
    }

    @GET
    @Path("{attachmentId}")
    public Response getFile(@PathParam("attachmentId") String attachmentId) throws Exception {
        // Authorization requires that the user is either the attachment owner or is authorized to view the identity
        // request to which the attachment belongs.

        Attachment attachment = getAttachment(attachmentId);

        if (attachment == null) {
            String err = new Message(ATTACHMENT_NOT_FOUND).getLocalizedMessage();
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }

        try {
            authorize(new AttachmentsAuthorizer(attachment));

            byte[] decryptedContent = AttachmentService.decrypt(attachment.getContent(), getContext());
            if (null != decryptedContent) {
                return Response.ok(decryptedContent, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + attachment.getName() + "\"")
                        .build();
            } else {
                String err = new Message(DOWNLOAD_ERROR).getLocalizedMessage();
                throw new GeneralException(err);
            }
        } catch (UnauthorizedAccessException uaex) {
            return Response.status(Response.Status.FORBIDDEN).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex).build();
        }
    }

    /**
     * Saves the given file to the database if attachments are enabled.
     * @param fileInputStream The file to be uploaded.
     * @param fileInfo Metadata for the uploaded file
     * @param attachmentContext contains list of requestees and transaction type (LCM_REQUEST)
     * @return An AttachmentDTO representation of the saved file.
     * @throws Exception
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@FormDataParam("file") InputStream fileInputStream,
                               @FormDataParam("file") FormDataContentDisposition fileInfo,
                               @FormDataParam("attachmentContext") Map<String, Object> attachmentContext) throws Exception {
        // Authorization requires that the user is authorized to make the access request contained in the attachment context.

        try {
            AttachmentsAuthorizer.authorizeUpload(this, attachmentContext);
        } catch(UnauthorizedAccessException e) {
            return Response.status(Response.Status.FORBIDDEN).entity(e.getLocalizedMessage()).build();
        }

        AttachmentService svc = new AttachmentService(this);
        if (!svc.isAttachmentsEnabled()) {
            String err = new Message(ATTACHMENTS_DISABLED).getLocalizedMessage();
            return Response.status(Response.Status.FORBIDDEN).entity(err).build();
        }

        if (fileInputStream == null) {
            String err = new Message(MISSING_FILE).getLocalizedMessage();
            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }

        int maxFileSizeConfig = svc.getMaxFileSize();
        int maxFileSize = maxFileSizeConfig * 1000 * 1000;

        // check content length header for large files
        // request content may contain some additional overhead bytes but it shouldn't be enough to
        // make a significant difference. this is faster than "sipping" the inputstream to check the input file size.
        // additional file size checking is on the front end.
        int contentLength = request.getContentLength();

        if (contentLength > maxFileSize) {
            String error = new Message(FILE_TOO_BIG, maxFileSizeConfig).getLocalizedMessage();
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }

        // Sanitize filename.
        String fileName = svc.sanitizeFileName(new String(fileInfo.getFileName().getBytes("iso-8859-1"), "UTF-8"));

        // Truncate file name.
        fileName = svc.truncateFileName(fileName);

        // Reject invalid filenames and extensions.
        String errs = svc.validateFile(fileName);
        if (!Util.isNullOrEmpty(errs)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(errs).build();
        }

        // Filename is good, upload it.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        boolean endOfStream = false;
        int totalBytesRead = 0;

        // only read in until the maxFileSize limit number of bytes or until stream is finished
        do {
            int bytesRead = fileInputStream.read(buffer);

            if (bytesRead == -1) {
                endOfStream = true;
                // done
                break;
            }

            output.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        } while(totalBytesRead < maxFileSize);

        if (!endOfStream) {
            // check if there is more data
            int bytesLeft = fileInputStream.read();
            if (bytesLeft != -1) {
                String error = new Message(FILE_TOO_BIG, maxFileSize).getLocalizedMessage();
                return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
            }
        }

        AttachmentDTO att = svc.createAttachment(fileName,getLoggedInUser(), output.toByteArray());
        return Response.ok(att).build();
    }

    /**
     * Sets the description of an attachment to the given value.
     * @param attachId Id of the attachment to set the description on
     * @param values Map of key value pairs. description is required to be part of the map.
     * @return
     * @throws GeneralException
     */
    @PATCH
    @Path("{attachmentId}")
    public Response setFileDescription(@PathParam("attachmentId") String attachId, Map<String, Object> values)
            throws GeneralException {
        // Authorization requires that the user is the attachment owner.

        AttachmentService svc = new AttachmentService(this);
        if (!svc.isAttachmentsEnabled()) {
            String err = new Message(ATTACHMENTS_DISABLED).getLocalizedMessage();
            return Response.status(Response.Status.FORBIDDEN).entity(err).build();
        }

        if (Util.isEmpty(values)) {
            throw new InvalidParameterException("values");
        }

        Attachment attachment = getAttachment(attachId);
        if (attachment == null) {
            String err = new Message(ATTACHMENT_NOT_FOUND).getLocalizedMessage();
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }

        // can't edit description if the attachment is already part of a requeest
        if (attachment.isInUse(getContext())) {
            String err = new Message(ATTACHMENT_IN_USE).getLocalizedMessage();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(err).build();
        }

        // Only the attachment uploader can change the description
        if (!attachment.getOwner().equals(getLoggedInUser())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        String desc = (String) values.get("description");
        if (desc == null) {
            String err = new Message(DESCRIPTION_MISSING).getLocalizedMessage();
            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }
        if (desc.length() > 200) {
            String err = new Message(DESCRIPTION_TOO_LONG).getLocalizedMessage();
            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }

        svc.setDescription(attachment,desc);
        return Response.ok().build();
    }

    /**
     * Remove attachment file from the database
     *
     * @param attachmentId attachment file id
     * @return Response
     * @throws GeneralException
     */
    @DELETE
    @Path("{attachmentId}")
    public Response removeFile(@PathParam("attachmentId") String attachmentId) throws GeneralException {
        // Authorization requires that the user is the attachment owner.

        // check to make sure attachment exists
        Attachment attachment = getAttachment(attachmentId);
        if (attachment == null) {
            String err = new Message(ATTACHMENT_NOT_FOUND).getLocalizedMessage();
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }

        // Check to make sure logged in user is owner
        if (!attachment.getOwner().equals(getLoggedInUser())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (attachment.isInUse(getContext())) {
            String err = new Message(ATTACHMENT_IN_USE).getLocalizedMessage();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(err).build();
        }

        getContext().removeObject(attachment);
        getContext().commitTransaction();

        return Response.ok().build();
    }
}
