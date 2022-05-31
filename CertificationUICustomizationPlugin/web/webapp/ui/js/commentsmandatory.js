/**
 *
 * @author Annie Hwang (annie.hwang@sailpoint.com)
 *------------------------------------------------*
 * Date: 01-15-2020
 * @author Ricardo Miquilarena (ricardo.miquilarena@sailpoint.com)
 * Changes for FDIC - Zendesk case #51231:
 * - Change Access Request Help button to point to an external URL
 * - Change Access Request Review tab text to denote that justification comments are mandatory
 * - Add parameters to the plugin to allow conditional implementation of these behaviors
 * - Implemented functions to retrieve these parameters
 */

var pageInitialized = false, isDebugEnabled = true, reviewBaseMsg, isCustomHelpPage = false, configHelpURL = '', arHelpButton, arSubmitButton,
    reviewTabSubtitleSpan, reviewTabChanged = false, helpBtnReconfigured = false, submitBtnReconfigured = false,
    jQueryClone = jQuery, messageCatalog = {}, originalSbmtBtn, reviewTabId = 'flowReviewBtn';

/*
 * #51231: Load IdentityIQ message catalog
 */
function loadMessageCatalog()
{
	jQueryClone.ajax(
  {
		'async': false,
		'method': 'GET',
    beforeSend: function (request)
    {
      request.setRequestHeader('X-XSRF-TOKEN', PluginHelper.getCsrfToken());
    },
    'url': SailPoint.CONTEXT_PATH + '/ui/rest/messageCatalog?lang=' + SailPoint.SYSTEM_LOCALE,
    'success': function(msg)
    {
      messageCatalog = msg;
      console.log('. . . ...Loaded the IdentityIQ message catalog');
    }
  });
}

/*
 * #51231: Retrieve the given IdentityIQ message from the message catalog
 */
function spTranslateMessage(key)
{
	var value = messageCatalog[key];
	if (typeof value === 'string' && value != '')
  {
    if(isDebugEnabled) console.log('. . . ...Got message ' + key + ':' + value);
		return value;
	}
	return key;
}

/*
 * #51231: Gets a Plugin configuration setting
 */
function getConfigSetting(endpoint)
{
  var http, settingValue, endpointURL = PluginHelper.getPluginRestUrl('justificationrequired/' + endpoint);
  jQueryClone.ajax(
  {
    'async': false,
    method: 'GET',
    beforeSend: function (request)
    {
      request.setRequestHeader('X-XSRF-TOKEN', PluginHelper.getCsrfToken());
    },
    url: PluginHelper.getPluginRestUrl('justificationrequired/' + endpoint)
  })
  .done(function (configMsg)
  {
    if(isDebugEnabled) console.log(endpoint + ' setting value:' + configMsg);
    settingValue = configMsg;
  });
  return settingValue;
};

/*
 * #51231: Gets a Plugin configuration setting
 * Has try, catch block in case rest service is unavailable
 */
function getConfigAttr(endpoint, defaultValue)
{
  var returnValue;
  try
  {
    returnValue = getConfigSetting(endpoint);
  }
  catch(e)
  {
    returnValue = defaultValue;
    if(isDebugEnabled) console.log(e);
  }
  return returnValue;
};

/*
 * #51231: If Help button is reconfigured it will run this
 * function to display the custom Help page
 */
function openHelpLink(helpURL)
{
  window.open(helpURL, 'popup_helpwindow', 'directories=0,toolbar=0,location=0,status=0,menubar=0,height=' + screen.availHeight + ',width=' + screen.availWidth);
};

/*
 * #51231: If Help or Submit button default behaviour is changes this
 * function will be called to reconfigure the button oclick event
 */
function reconfigureButton(thisButton, newButton, btnReconfigured)
{
  if(isDebugEnabled) console.log('Entering reconfigureButton function...' + thisButton.attr('id'));
  try
  {
    if(thisButton.length == 1)
    {
      var onClick = thisButton.attr('ng-click') || thisButton.attr('onclick');
      if(isDebugEnabled) console.log('Button original onclick...' + onClick==null?'no onclick or ng-click defined':onClick);
      thisButton.replaceWith(newButton);
      thisButton = $(thisButton.attr('id'));
      onClick = thisButton.attr('ng-click') || thisButton.attr('onclick');
      if(isDebugEnabled) console.log('Button new onclick...' + onClick==null?'no onclick or ng-click defined':onClick);
      btnReconfigured = true;
    }
  }
  catch(e)
  {
    if(isDebugEnabled) console.log(e);
  }
  if(isDebugEnabled) console.log('Exiting reconfigureButton function...' + onClick);
  return btnReconfigured;
};

/*
 * #51231: Function hides the OOTB Submit button and adds a button that will check if all comments have been entered
 */
function reconfigureSubmitButton(thisButton, btnReconfigured)
{
    if(isDebugEnabled) console.log('Reconfiguring Submit button...' + btnReconfigured);
    var btnId = thisButton[0].getAttribute('id');
    if(isDebugEnabled) console.log('Submit button Id:' + btnId);
    var originalSbmtBtn = $('#' + btnId);
    originalSbmtBtn.addClass('hidden');
    var jcSubmitBtn = '<button id="checkCmmntsBtn" class="btn btn-s-sm btn-primary ng-binding ng-scope"' +
                    ' type="button" onclick="checkJustifications(itemsMissingComment)">Submit</button>';
    originalSbmtBtn.parent().append(jcSubmitBtn);
    var config = {subtree: true, attributes: true};
    $(".btn.btn-progress").each(function(index, element)
    {
      setObserver(element, config);
    });
    return true;
};

/*
 * #51231: This function is used to track what tab has been selected
 * If we are not in the Review tab, hide the custom Submit button
 */
function setObserver(target, config)
{
  var buttonId, elementClass, isButton = false, tabSelected = false;
  var observer = new MutationObserver(function(mutations)
  {
    mutations.forEach(function(mutation)
    {
      buttonId = mutation.target.id;
      isButton = mutation.target.type==='button';
      elementClass = $(mutation.target).prop(mutation.attributeName);
      tabSelected = elementClass==='btn btn-progress progress-active';
      if(isDebugEnabled && isButton)
      {
        console.log("mutation.target.id:" + buttonId);
        console.log('Element type:' + mutation.target.type + '...');
      }
      if (isButton && tabSelected)
      {
        if(isDebugEnabled) console.log('Tab Selected...' + buttonId);
        document.getElementById('checkCmmntsBtn').style.display = buttonId!==reviewTabId?'none':'';
      }
    });
  });
  observer.observe(target, config);
}

/*
 * #51231: This function will be called to determine if any justification comment is missing
 */
function checkJustifications(itemsMissingComment)
{
  var submitRequest = itemsMissingComment.length===0,
      popupMessage = '', message;
  $.each(
    itemsMissingComment, function(indx, listEntry)
    {
      message = listEntry.replace('Comment for ', 'Comment for <b>');
      message = message.replace(' must be entered', '</b> must be entered');
      popupMessage += '<h5>' + message + '</h5>';
    }
  );

  if (submitRequest)
  {
    /* All justifications have been entered, call the OOTB Submit button to submit the access request */
    if(isDebugEnabled) console.log('All justifications have been entered, calling the OOTB Submit button to submit the access request');
    $('#submitBtn').click();
  }
  else
  {
    /* Justifications are missing, call function to display modal screen */
    showModal(popupMessage);
  }
};

/*
 * #51231: This function is called to display the modal screen listing missing justifications
 */
function showModal(missingCommentsList)
{
	var modal = document.getElementById('missingCmmnt');
	modal.style.display = 'block';
	var divMssngCmmntsList = jQuery('div#missingCmmntsList');
  var divContent = divMssngCmmntsList.find('h5');
  if(divContent.length > 0)
  {
    if(isDebugEnabled) console.log('Cleaning old justification list...');
    divContent.remove();
  }
  divMssngCmmntsList.append('<h5/>' + missingCommentsList);
}

/*
 * #51231: This function is called to close the modal screen
 */
function hideModal()
{
	var modal = document.getElementById('missingCmmnt');
	modal.style.display = 'none';
}

function toggleSubmitButtons(ootbSubmitBtn)
{
  /* #51231: If the OOTB Submit button is enable by default then the
   * button will remain hidden until all justifications have been entered
   */
  if(ootbSubmitBtn[0].style.display !== 'none')
  {
    if(isDebugEnabled) console.log('Hiding OOTB Submit button');
    ootbSubmitBtn[0].style.display = 'none';
  }
}

/*
 * #51231: This function initializes the modal screen that will display the missing justification
 */
function initializeMissingCommentsPopUp()
{
  if(isDebugEnabled) console.log('Initializing missing justification comments modal popup...');
  var alertDiv = '<div id="missingCmmnt" uib-modal-window="modal-window" class="modal fade in modalBackground" role="dialog" index="0" animate="animate" tabindex="-1" aria-hidden="true">' +
        '<div class="modal-dialog modalDialog" aria-labelledby="modalTitle">' +
          '<div class="modal-content modalContent">' +
            '<div id="missingInfoModal" role="alertdialog" class="alert-modal">' +
              '<div role="dialog" class="modal-header modalHeader">' +
                '<button data-dismiss="modal" aria-hidden="true" id="missingCloseModalDialogBtn" type="button" class="close ng-isolate-scope" aria-label="Close dialog" onclick="hideModal()">' +
                  '<i role="presentation" class="fa fa-times"></i>' +
                '</button>' +
                '<h4 class="alert-heading">' +
                  "<!-- ngIf: warningLevel==='warn' || warningLevel==='danger'-->" +
                  '<i class="fa fa-exclamation-triangle ng-scope" ng-if="warningLevel===' + "'warn' || warningLevel==='danger'" + ' style="color: #ff9900"></i>' +
                  "<!-- end ngIf: warningLevel==='warn' || warningLevel==='danger' -->" +
                  "<!-- ngIf: !warningLevel || warningLevel==='info' --> Missing Mandatory Justifiction Comments</h4>" +
              '</div>' +
              '<div id="missingCmmntsList" class="modal-body modal-warn modalBody"/>' +
            '</div></div></div></div>';

  jQuery('div.sp-body').before(alertDiv);
	var modal = document.getElementById('missingCmmnt');
  return alertDiv;
}

/*
 * #51231: If the checkbox “Apply to All Items” is clicked this function is
 * called to propagate the entered justification message to all comment fields
 */
function propagateJustificationMessage(commentPopUpButton, isDebugEnabled, comment4all, tabHeading, isAssignmentNote)
{
  if(isDebugEnabled)
  {
    console.log('Justification ' + tabHeading + '...' + comment4all);
    console.log('Determining if will propagate justification to "' + commentPopUpButton.getAttribute("aria-label") + '"...');
  }
  commentPopUpButton.click(function(isDebugEnabled, comment4all)
  {
    if(isDebugEnabled) console.log('Retrieving request item textarea object... . . .');
  });

  /* #51231: textarea tag id for role textareas are comment & assignmentNote and for entitlement is commentTextArea */
  var riTextArea;
  if(isAssignmentNote)
  {
    riTextArea = jQuery.find("textarea[ng-model*='ctrl.assignmentNote']");
  }
  else
  {
    riTextArea = jQuery.find("textarea[ng-model*='ctrl.comment']");
  }
  /* #51231: Comment originated from a Role request item Assignment Note
   * - Cannot propagate to any entitlement requested item, as they only have a comment text area
   */
  if(riTextArea.length >= 1)
  {
    if(isDebugEnabled) console.log("Saving justification... . . .");
    riTextArea[0].value = comment4all;
    $('#' + riTextArea[0].id).trigger('change');
    var saveCommentBtn = $('#saveCommentBtn');
    if(saveCommentBtn.length >= 1)
    {
      if(isDebugEnabled) console.log("Executing Save button click function...");
      saveCommentBtn[0].click(function(){ctrl.saveComment();});
    }
  }
  dismissing = jQuery.find("button[ng-click*='$dismiss()']");
  if (dismissing.length >= 1)
  {
    dismissing[0].click(function(){$dismiss();});
  }
}

jQuery(document).ready(function()
{
  loadMessageCatalog();
  var missingCmmntsDiv = initializeMissingCommentsPopUp();
  isDebugEnabled = getConfigAttr('isLogEnabled', false);
  isCustomHelpPage = getConfigAttr('isCustomHelpPage', false);
  if(isCustomHelpPage)
  {
    configHelpURL = getConfigAttr('getHelpURL', SailPoint.CONTEXT_PATH + '/contextualhelp?helpKey=manageUserAccessURL');
  }
	MutationObserver = window.MutationObserver || window.WebKitMutationObserver || window.MozMutationObserver;

	var observer = new MutationObserver(function(mutations, observer)
  {
    /* #51231: Sets Review tab subtitle and reconfigures the Help button */
    if(!pageInitialized)
    {
      if(!reviewTabChanged)
      {
        try
        {
          /* #51231: Add Review tab subtitle text to denote that justification comments are mandatory */
          if(isDebugEnabled) console.log('Reconfiguring Review tab subtitle...');
          reviewTabSubtitleSpan = $('#' + reviewTabId).find('.hidden-xs.subtitle.ng-binding');
          if(isDebugEnabled) console.log('Review tab subtitle...' + reviewTabSubtitleSpan[0].innerText);
          reviewBaseMsg = spTranslateMessage('ui_access_review_subtitle');
          var reviewTabMessage = getConfigAttr('getReviewTabMessage', 'Comment red icon indicates that justification must be entered.');
          if(isDebugEnabled) console.log('Review tab subtitle suffix...' + reviewTabMessage);
          reviewTabSubtitleSpan.text(reviewBaseMsg + ' ' + reviewTabMessage);
          console.log('Review tab subtitle...' + reviewTabSubtitleSpan[0].innerText);
          reviewTabChanged = true;
        }
        catch(e)
        {
          if(isDebugEnabled) console.log(e);
        }
      }

      if(isCustomHelpPage && !helpBtnReconfigured)
      {
        /* #51231: Change Access Request Help button to point to an external URL */
        if(isDebugEnabled) console.log('Reconfiguring Help button...');
        /*
         * #51231: Get the Help URL from the Plugin configuration
         * Is the URL of the Help page that will be invoke when pressing the Access Request screen Help button.
         * Find the implemenation in PageConfigResource.java
         */
        var newButton = '<button ng-if="spEnabled" id="contextualHelpManageUserAccessURL-help-btn" type="button" ng-attr-uib-tooltip="{{spType === \'Popup\' ? spTooltip : undefined}}"' +
            'onclick="openHelpLink(configHelpURL)" aria-label="Click to show help for Manage User Access." class="btn icon-btn btn-white pull-right">' +
            '<i class="fa fa-question-circle" role="presentation"></i>   Help </a>';
        arHelpButton = $('#contextualHelpManageUserAccessURL-help-btn');
        helpBtnReconfigured = reconfigureButton(arHelpButton, newButton, helpBtnReconfigured);
      }
      pageInitialized = reviewTabChanged && helpBtnReconfigured;
    }

		for (var i = 0; i < mutations.length; i++)
    {
			var mutation = mutations[i];
			for (var i2 = 0; i2 < mutation.addedNodes.length; i2++) {
				var saveClickedButton = jQuery(mutation.addedNodes[i2]).find("button[ng-click*='ctrl.saveComment()']");
				if(saveClickedButton.length == 1) {
					var rcdmt = jQuery.find("h4[id*='modalTitle']");
					if (rcdmt.length >= 1) {
						var title = rcdmt[0].innerText;
						if (title.indexOf("Justification") == -1) {
							var itemName = "";
							itemName = angular.element('.modal').scope().$resolve.requestedAccessItem.item.attributes.displayableName || angular.element('.modal').scope().$resolve.requestedAccessItem.item.attributes.name;
							rcdmt[0].innerText = "Required Justification";
							if (itemName.length > 0) rcdmt[0].innerText += " - " + itemName;
							var a2a = jQuery.find("[id*='apply2all']");
							if (a2a.length < 1) {
								/* Adds a checkbox to dialog box to apply the same comment to all requested items */
                jQuery("div.panel-footer button:last").before('<input type="checkbox" id="apply2all"><b>&nbsp;Apply to All Items &nbsp;&nbsp;</b></input>');
							}
						}
					}
				}

				var commTabPanelQuery = jQuery(mutation.addedNodes[i2]).find("div[id='commentTabPanel']");
				//if(isDebugEnabled) console.log("*** commTabPanelQuery.length: "+commTabPanelQuery.length);
				if (commTabPanelQuery.length == 1)
        {
          /* #51231: Remove 'ng-hide' from className as it was hiding the comment Text Area for requested role items */
				  commTabPanelQuery[0].className = "panel-body ng-scope";
				}

				var commPanelQuery = jQuery(mutation.addedNodes[i2]).find("div[id='commentPanel']");
				//if(isDebugEnabled) console.log("*** commPanelQuery.length: "+commPanelQuery.length);
				if (commPanelQuery.length == 1) {
				  commPanelQuery[0].className = "panel-body ng-scope";
				}
			}
		}

	});

	$('body').on('click', '#saveCommentBtn', function (event) {
		//this.disabled = true;

	  /* #51231: Textarea Ids
     * Entitlements: commentTextArea
     * Roles: comment for Comment tab & assignmentNote for Assignment Note tab
     */
	  if(document.getElementById('apply2all').checked)
    {
      var apply2AllCheckbox = document.getElementById('apply2all');
      apply2AllCheckbox.checked = false;
      var tabHeading = "Comment";
      var isAssignmentNote = false;
      var roleTabsPanel = jQuery('div#commentTabPanel');
      if(roleTabsPanel.length>0)
      {
        /* Comment originated from a Role request item - Need to know if it is a comment or assignment note */
        tabHeading = roleTabsPanel.find('.uib-tab.nav-item.ng-scope.ng-isolate-scope.active').attr('heading');
        if(isDebugEnabled) console.log('Comment entered from a Role request item ' + tabHeading + ' tab...');
        var textareaId = tabHeading.charAt(0).toLowerCase() + tabHeading.replace(/\s/g, '').substr(1);
        isAssignmentNote = textareaId==='assignmentNote';
        top.cmt4all = jQuery.find("textarea[id*='" + textareaId + "']")[0].value;
      }
      else
      {
        top.cmt4all = jQuery.find("textarea[id*='commentTextArea']")[0].value;
      }
      if(isDebugEnabled) console.log('Apply to All - checked - top.cmt4all...' + top.cmt4all);

      var cd = jQuery.find("button[ng-click*='reviewCtrl.showCommentDialog(requestedItem)']");
      var cd2 = jQuery.find("button[ng-click*='reviewCtrl.showCommentDialog(removedItem)']");

      var rcds = jQuery.find("button[id*='saveCommentBtn']");
      var dm = jQuery.find("button[ng-click*='$dismiss()']");

      if (cd.length > 0)
      {
        for (var i=0; i<cd.length; i++)
        {
          /* #51231: If the checkbox “Apply to All Items” is clicked then need to propagate the entered justification message to all comment fields */
          propagateJustificationMessage(cd[i], isDebugEnabled, top.cmt4all, tabHeading, isAssignmentNote);
        }
      }
      if (cd2.length > 0) {
        for (var i=0; i<cd2.length; i++)
        {
          /* #51231: If the checkbox “Apply to All Items” is clicked then need to propagate the entered justification message to all comment fields */
          propagateJustificationMessage(cd2[i], isDebugEnabled, top.cmt4all, tabHeading, isAssignmentNote);
        }
      }
	  } // end of if checkbox checked?
	});

	// define what element should be observed by the observer
	// and what types of mutations trigger the callback
	observer.observe(document, {
		childList: true,
		subtree: true,
		attributes: false

		//...
	});

});
