var url = SailPoint.CONTEXT_PATH + '/plugins/pluginPage.jsf?pn=workitemarchiveextensionplugin';  

jQuery(document).ready(function(){
   jQuery("li:contains('My Access Reviews')")
     .before(
    	'<li role="presentation">' +  
        '  <a class="menuitem" href="' + url + '" role="menuitem">WorkItemArchive' +   
        '  </a>' +  
        '</li>'  
     );
})