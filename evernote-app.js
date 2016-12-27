var app = angular.module('evernote-app',[]);

/**
 * Utility Service for EVERNOTE API system
 **/
app.service('util',[function(){
	var loader = {
			show : false
	};
	return {
		/**
		 * Method to get Request prarmeters from URL
		 **/
		getURLParameter : function(sParam){
		    var sPageURL = decodeURIComponent(window.location.search.substring(1)),
		        sURLVariables = sPageURL.split('&'),
		        sParameterName,
		        i;

		    for (i = 0; i < sURLVariables.length; i++) {
		        sParameterName = sURLVariables[i].split('=');

		        if (sParameterName[0] === sParam) {
		            return sParameterName[1] === undefined ? true : sParameterName[1];
		        }
		    }
		},
		showLoader : function(){
			loader.show = true;
		},
		hideLoader : function(){
			loader.show = false;
		},
		getLoader : function(){
			return loader;
		}
	};
}]);
app.service('evernote-api',['$http','util',function($http,util){
	var notesList = {
			everNoteNoteBooks : null,
			everNoteNotes : null,
			everNoteNotesContent : null
	};
	return {
		setEverNoteBookInNotesList : function(notes){
			notesList.everNoteNoteBooks = notes;
		},
		setEverNoteInNotesList : function(notes){
			notesList.everNoteNotes = notes;
		},
		setEverNoteContentInNotesList : function(notes){
			notesList.everNoteNotesContent = notes;
		},
		fetchNoteBooks : function(parameterMap){
			var self = this;
			var urlToHit = 'rest/getNoteBooks?redirectToUrl=http://localhost:8080/evernote/';
			if(typeof parameterMap === 'object'){
				urlToHit = urlToHit+'&';
				$.each( parameterMap, function( key, value ) {
					urlToHit = urlToHit+'&'+key+'='+value;
				});
			}
			util.showLoader();
			$http
			.get(urlToHit)
				.success(function(data){
					self.setEverNoteBookInNotesList(data);
					util.hideLoader();
				})
				.error(function(data){
					util.hideLoader();
					if(data.redirectToAuthorize){
						window.location.href = (data.authorizationURL);
					}
				});
		},
		fetchNotes : function(parameterMap){
			var self = this;
			var urlToHit = 'rest/getNotes?redirectToUrl=http://localhost:8080/evernote/';
			if(typeof parameterMap === 'object'){
				urlToHit = urlToHit+'&';
				$.each( parameterMap, function( key, value ) {
					urlToHit = urlToHit+'&'+key+'='+value;
				});
			}
			util.showLoader();
			$http
			.get(urlToHit)
				.success(function(data){
					self.setEverNoteInNotesList(data);
					util.hideLoader();
					self.setEverNoteContentInNotesList("");
					util.hideLoader();
				})
				.error(function(data){
					util.hideLoader();
					if(data.redirectToAuthorize){
						window.location.href = (data.authorizationURL);
					}
				});
		},
		fetchNotesContent : function(parameterMap){
			var self = this;
			var urlToHit = 'rest/getNotesContent?redirectToUrl=http://localhost:8080/evernote/';
			if(typeof parameterMap === 'object'){
				urlToHit = urlToHit+'&';
				$.each( parameterMap, function( key, value ) {
					urlToHit = urlToHit+'&'+key+'='+value;
				});
			}
			util.showLoader();
			$http
			.get(urlToHit)
				.success(function(data){
					console.log("Content success:",data);
					self.setEverNoteContentInNotesList(data);
					util.hideLoader();
				})
				.error(function(data){
					console.log("error:",data);
					util.hideLoader();
					if(data.redirectToAuthorize){
						window.location.href = (data.authorizationURL);
					}
				});
		},
		getNotes : function(){
			return notesList;
		}
	};
}]);
app.filter('renderHTML',['$sce',function(sce){
	return function(input){
        return sce.trustAsHtml(input);
	};
}]);
app.run(['util','evernote-api',function(util,evernoteApi){
	console.info('App Run intitated');
	var callBackParamValue = util.getURLParameter("callbackaction");
	var oauthToken = util.getURLParameter("oauth_token");
	var oauthVerifier = util.getURLParameter("oauth_verifier");
	if(callBackParamValue === "evernote_fetch_notebooks"){
		var params1 = {};
		params1.oauth_token = oauthToken;
		params1.oauth_verifier = oauthVerifier;
		evernoteApi.fetchNoteBooks(params1);
	} else if(callBackParamValue === "evernote_fetch_notes"){
		var params2 = {};
		params2.oauth_token = oauthToken;
		params2.oauth_verifier = oauthVerifier;
		evernoteApi.fetchNotes(params2);
	}else if(callBackParamValue === "evernote_fetch_notesContent"){
		var params3 = {};
		params3.oauth_token = oauthToken;
		params3.oauth_verifier = oauthVerifier;
		evernoteApi.fetchNotesContent(params3);
	}
}]);
app.controller('evernote-controller',['$scope',
                                      'evernote-api',
                                      'util',
                                      function($scope,evernoteApi,util){
	console.info('Inside Controller');
	$scope.fetchNoteBooks = function(){
		evernoteApi.fetchNoteBooks();
		util.showLoader();
	};
	$scope.fetchNotes = function(guId){
		console.log(guId);
		var params = {};
		params.noteBookGuid = guId;
		evernoteApi.fetchNotes(params);
		util.showLoader();
	};
	$scope.fetchNotesContent = function(notesGuId){
		console.log(notesGuId);
		var params = {};
		params.noteGuid = notesGuId;
		evernoteApi.fetchNotesContent(params);
		util.showLoader();
		};
	$scope.notes = evernoteApi.getNotes();
	$scope.everNoteLoading = util.getLoader();
 }]);
app.config(['$httpProvider',
            function($httpProvider){
	console.info('App configured');
	var transformResponseFunction = function(data){
		try{
			data = JSON.parse(data);
		} catch (e){
			console.log(e);
		}
		return data;
	};
	$httpProvider.defaults.transformResponse = [transformResponseFunction];
}]);