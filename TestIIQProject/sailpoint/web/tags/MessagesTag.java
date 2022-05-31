package sailpoint.web.tags;

import java.io.IOException;

import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

/**
 * This is used to support javascript side messages.
 * 
 * 
 * <sp:messages name='reminderMessages'/>
 *  
 *  Will be rendered as
 *  
 * <div id='$name' style='display:none'>
 *   <div id='$name-closeImg' class='close-img'/>
 *   <ul id='$name-list' class='message-list'>
 *   </ul>
 * </div>
 * <script>
 *   var $name;
 *   Ext.onReady(function() {
 *     $name = Ext.create('sailpoint.message.Messages', {"id" : "$name"});
 *   });
 * </script>
 * 
 * 
 * Once this component is rendered you would be able to do the following
 * reminderMessage.show[Info|Error|Warning|Success|Validation]('the message');
 * reminderMessage.addMessage('another message');
 * reminderMessage.close();
 * 
 * @author Tapash
 *
 */
public class MessagesTag extends UIOutput {

    private ResponseWriter writer;
    private String name;
    
    public String getRendererType() {
    
        return null;
    }
    
    public void encodeBegin(FacesContext context) throws IOException {
    
        writer = context.getResponseWriter();
        
        name = (String) getAttributes().get("name");
        
        writer.startElement("div", this);
        writer.writeAttribute("id", name, null);
        writer.writeAttribute("style", "display:none", null);
        
        writeImgDiv();
        writeUL();
        
        writer.endElement("div");

        writeScript();
    }
    
    private void writeImgDiv() throws IOException {
        
        writer.startElement("div", this);
        writer.writeAttribute("id", name + "-closeImg", null);
        writer.writeAttribute("class", "close-img", null);
        writer.endElement("div");
    }

    private void writeUL() throws IOException {
        
        writer.startElement("ul", this);
        writer.writeAttribute("id", name + "-list", null);
        writer.writeAttribute("class", "message-list", null);
        writer.endElement("ul");
    }
    
    private void writeScript() throws IOException {
        
        StringBuilder sb = new StringBuilder();
        sb.append("<script type='text/javascript'>").append("\n");
        sb.append("var ").append(name).append(";").append("\n");
        sb.append("Ext.onReady(function() {");
        sb.append("  ").append(name).append(" = Ext.create('sailpoint.message.Messages', {\"id\" : \"").append(name).append("\"});").append("\n");
        sb.append("});").append("\n");
        sb.append("</script>").append("\n");
        writer.write(sb.toString());
    }
}
