var url = SailPoint.CONTEXT_PATH + '/plugins/pluginPage.jsf?pn=TrainingPlugin';  

jQuery(document).ready(function(){
   jQuery("ul.navbar-left li:contains('Intelligence') ul li.divider")
     .before(
    	'<li role="presentation">' +  
        '  <a class="menuitem" href="' + url + '" role="menuitem">Object Search' +   
        '  </a>' +  
        '</li>'  
     );
})
    