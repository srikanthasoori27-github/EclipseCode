/**
 *
 * @author Annie Hwang (annie.hwang@sailpoint.com)
 *------------------------------------------------*
 * Date: 01-15-2020
 * @author Ricardo Miquilarena (ricardo.miquilarena@sailpoint.com)
 * Changes for FDIC - Zendesk case #51231:
 * - A tooltip is added to the Comment fields indicating where a mandatory justification needs to be entered
 * - Allow for the Access Request Review tab Submit button to not be disabled by default
 * - Plugin configuration parameters have been added to allow the submit button to either be enabled or disabled
 *   - If it is disabled by default then it remains disabled until all comments are entered - Current default behavior
 *   - If it is enable by default then a new button is added to run
 *     a function that will verify if all comments have been entered
 *   - Once all comments have been entered the OOTB Submit button will be programatically clicked
 * - Eliminate initial popup, firstPop is set to true by default
 */

var isDefaultDisabled = true, /* Indicates if Access Request Review tab Submit button is disabled by default */
    itemsMissingComment = new Array(), navigationBarDiv;

jQuery(document).ready(function(){

var cd = jQuery.find("button[ng-click*='dataTableCtrl.spCheckboxMultiselect.getSelectionModel().selectAll()']");
if ( cd.is(":visible") ) { 
    cd.hide(); 
  }
	
});
