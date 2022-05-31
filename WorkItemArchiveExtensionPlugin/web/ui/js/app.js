/**
 * Create the module.
 */

var ksModule = angular.module('workitemarchiveextensionplugin', ['ui.bootstrap', 'sailpoint.i18n',
  'sailpoint.comment', 'sailpoint.email', 'sailpoint.esig',
  'sailpoint.identity.account', 'sailpoint.modal',
  'sailpoint.util', 'sailpoint.tree', 'sailpoint.ui.bootstrap.carousel',
  'sailpoint.dataview', 'sailpoint.config']);
  
ksModule.config(['$httpProvider',function($httpProvider) {
   $httpProvider.defaults.xsrfCookieName = "CSRF-TOKEN";  		  
}])

/**
 * Controller for the Kitchen Sink plugin.
 */
ksModule.controller('workitemarchiveextensionpluginController', [ '$scope','$http', function($scope,$http) {
	$scope.welcomeMessage="Hello, World!";
	 $scope.results = "Initial values";
   $scope.objects = "Some values";  
	
	 $http({
      method: 'GET',
	     url: PluginHelper.getPluginRestUrl("workitemarchiveExtension/workitems")
    }).then(function successCallback(response) {
		   try {
		          $scope.objects = response.data;
	       }  catch(err) {
               $scope.directions= "Search error!";
               $scope.objects = ["Search error: could not parse response"];           
           }
	   }, function errorCallback(response) {
             $scope.directions= "Search error: unable to get list of objects!";
           
	   });
}]

);
