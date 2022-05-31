angular.module('TrainingPlugin',[])
.config(['$httpProvider',function($httpProvider) {
   $httpProvider.defaults.xsrfCookieName = "CSRF-TOKEN";  		  
}])
 
.controller('trainingPluginFullPageCtrl', ['$scope','$http', function($scope, $http) {

   //Variable declarations
   $scope.results = "";
   $scope.objects = "";   
   //-----------
   // populate list of searchable objects from plugin database
   $http({
      method: 'GET',
	     url: PluginHelper.getPluginRestUrl("TrainingPlugin/search/objectNames")
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
	   
	   // function called when enter key is pressed in input field
   $scope.searchEnter = function(event) { 
     if (event.keyCode == 13) { 
       $scope.getSearch($scope.object,$scope.search); 
     }
   } 
   
   // function called when button is pressed
   $scope.getSearch = function(object,search) { 
	   $http({
          method: "GET",
          // use PluginHelper to get full URL for rest call
          url: PluginHelper.getPluginRestUrl("TrainingPlugin/search/" + object + "," + search), 
	   }).then(function successCallback(response) {
           try {
        	   let json = response.data;
               $scope.directions= "Results:";
               if (json === undefined || json.length == 0) {
             	  $scope.results = ["No matches found"]; 
               } else {
		          $scope.results = json;
               }
           }  catch(err) {
               $scope.directions= "Search error!";
               $scope.results = ["You asked to search for " + $scope.search + " in " + $scope.object];
           } 
	    }, function errorCallback(response) {
	    	 $scope.directions= "Search error!";
		     $scope.results = response.status;
	    }) //end http .then
	    
    }; // end getSearch  
	   

}]);

