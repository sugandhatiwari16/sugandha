package com.tcs.ultimatix.util.evernote.service;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.EvernoteApi;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteSortOrder;
import com.evernote.edam.type.Notebook;
import com.evernote.thrift.TException;
import com.tcs.ultimatix.util.evernote.dto.SSRNote;
import com.tcs.ultimatix.util.evernote.dto.SSRNoteBook;
import com.tcs.ultimatix.util.evernote.exception.EvernoteApiException;

@Service
public class NoteServiceImpl implements NoteService {

	private static final Logger LOG = Logger.getLogger(NoteServiceImpl.class);
	private static final EvernoteService EVERNOTE_SERVICE = EvernoteService.SANDBOX;
	private static final String CONSUMER_KEY = "smartsalespoc";
	private static final String CONSUMER_SECRET = "15df875d773cf7d5";

	@Autowired
	HttpSession session;

	public String doAuthenticate(HttpServletRequest request,
			String callBackAction) throws EvernoteApiException {
		/*
		 * Setting Proxy Values
		 */
		System.setProperty("http.proxyHost", "proxy.tcs.com");
		System.setProperty("http.proxyPort", "8080");
		System.setProperty("https.proxyHost", "proxy.tcs.com");
		System.setProperty("https.proxyPort", "8080");
		System.setProperty("proxySet", "true");
		/*
		 * Proxy Values Set
		 */

		String othVer = (String) session.getAttribute("oauthVerifier");
		if (othVer == null) {
			othVer = request.getParameter("oauth_verifier");
			othVer = normalizeString(othVer);
		}
		String othTok = (String) session.getAttribute("oauthToken");
		if (othTok == null) {
			othTok = request.getParameter("oauth_token");
			othTok = normalizeString(othTok);
		}
		String redToResourceLoc = request.getParameter("redirectToUrl");
		redToResourceLoc = normalizeString(redToResourceLoc);

		/*
		 * Initialize Variables
		 */
		String reqTokSec = (String) session
				.getAttribute("requestTokenSecret");
		OAuthService service = null;
		String reqTok;
		String accessTok = (String) session.getAttribute("accessToken");

		if (redToResourceLoc != null) {
			// This url would be used as a callback URL -- evernote_fetch_notes
			redToResourceLoc = redToResourceLoc + "?callbackaction=" + callBackAction;
		}
		// Change Sandbox to production while deploying in production
		Class<? extends EvernoteApi> providerClass = EvernoteApi.Sandbox.class;
		if (EVERNOTE_SERVICE == EvernoteService.PRODUCTION) {
			providerClass = EvernoteApi.class;
		}
		service = new ServiceBuilder().provider(providerClass)
				.apiKey(CONSUMER_KEY).apiSecret(CONSUMER_SECRET)
				.callback(redToResourceLoc).build();
		if (othVer == null) {
			Token scribeReqTok = service.getRequestToken();
			reqTok = scribeReqTok.getToken();
			reqTokSec = scribeReqTok.getSecret();
			session.setAttribute("requestTokenSecret", reqTokSec);
			String othResourceLoc = EVERNOTE_SERVICE
					.getAuthorizationUrl(reqTok);
			throw new EvernoteApiException(othResourceLoc, true);
		} else {
			session.setAttribute("oauthVerifier", othVer);
			session.setAttribute("oauthToken", othTok);
			/** Getting Access Token **/
			if (accessTok == null) {
				Token scribeReqTokForAccss = new Token(othTok,
						reqTokSec);
				Verifier scribeVer = new Verifier(othVer);
				Token scribeAccssTok = service.getAccessToken(
						scribeReqTokForAccss, scribeVer);
				EvernoteAuth evernoteAuth = EvernoteAuth.parseOAuthResponse(
						EVERNOTE_SERVICE, scribeAccssTok.getRawResponse());
				accessTok = evernoteAuth.getToken();
				session.setAttribute("accessToken", accessTok);
			}
		}
		return accessTok;
	}

	public List<SSRNoteBook> getNoteBooks(HttpServletRequest request)
			throws EvernoteApiException {

		List<SSRNoteBook> ssrNoteBooks = new ArrayList<SSRNoteBook>();

		try {

			NoteStoreClient noteStoreClient = validateSessionAndGetNotestoreClient(
					request, "evernote_fetch_notebooks");
			List<Notebook> notebooks = noteStoreClient.listNotebooks();
			for (Notebook notebook : notebooks) {
				SSRNoteBook ssrNoteBook = new SSRNoteBook();
				ssrNoteBook.setName(notebook.getName());
				ssrNoteBook.setGuId(notebook.getGuid());

				ssrNoteBooks.add(ssrNoteBook);
			}
		} catch (EDAMUserException e1) {
			LOG.info("error occured", e1);
			throw new EvernoteApiException(e1.getMessage());
		} catch (EDAMSystemException e1) {
			LOG.info("error occured", e1);
			throw new EvernoteApiException(e1.getMessage());
		} catch (TException e1) {
			LOG.info("error occured", e1);
			throw new EvernoteApiException(e1.getMessage());
		}

		return ssrNoteBooks;
	}

	public List<SSRNote> getNotes(HttpServletRequest request,
			String noteBookGuid) throws EvernoteApiException {
		List<SSRNote> ssrNotes = new ArrayList<SSRNote>();

		try {

			NoteStoreClient noteStoreClient = validateSessionAndGetNotestoreClient(
					request, "evernote_fetch_notes");
			NoteFilter filter = new NoteFilter();
			filter.setNotebookGuid(noteBookGuid);
			filter.setOrder(NoteSortOrder.CREATED.getValue());
			filter.setAscending(true);
			NoteList noteList = noteStoreClient.findNotes(filter, 0, 100);
			List<Note> notes = noteList.getNotes();
			for (Note note : notes) {
				SSRNote evernoteNote = new SSRNote();
				evernoteNote.setNoteTitle(note.getTitle());
				evernoteNote.setNoteGuId(note.getGuid());
				ssrNotes.add(evernoteNote);
			}
		} catch (EDAMUserException e2) {
			LOG.info("error occured", e2);
			throw new EvernoteApiException(e2.getMessage());
		} catch (EDAMSystemException e2) {
			LOG.info("error occured", e2);
			throw new EvernoteApiException(e2.getMessage());
		} catch (TException e2) {
			LOG.info("error occured", e2);
			throw new EvernoteApiException(e2.getMessage());
		} catch (EDAMNotFoundException e2) {
			LOG.info("error occured", e2);
			throw new EvernoteApiException(e2.getMessage());
		}
		return ssrNotes;
	}

	public List<SSRNote> getNotesContent(HttpServletRequest request,
			String noteGuid) throws EvernoteApiException {
		List<SSRNote> ssrNotes = new ArrayList<SSRNote>();

		try {

			NoteStoreClient noteStoreClient = validateSessionAndGetNotestoreClient(
					request, "evernote_fetch_notesContent");

			SSRNote evernoteNote = new SSRNote();
			Note fullNote = noteStoreClient.getNote(noteGuid, true, false,
					false, false);
			evernoteNote.setNoteContent(fullNote.getContent());
			ssrNotes.add(evernoteNote);

		} catch (EDAMUserException e3) {
			LOG.info("error occured", e3);
			throw new EvernoteApiException(e3.getMessage());
		} catch (EDAMSystemException e3) {
			LOG.info("error occured", e3);
			throw new EvernoteApiException(e3.getMessage());
		} catch (TException e3) {
			LOG.info("error occured", e3);
			throw new EvernoteApiException(e3.getMessage());
		} catch (EDAMNotFoundException e3) {
			LOG.info("error occured", e3);
			throw new EvernoteApiException(e3.getMessage());
		}
		return ssrNotes;
	}

	private NoteStoreClient validateSessionAndGetNotestoreClient(
			HttpServletRequest request, String callback)
					throws EvernoteApiException {
		NoteStoreClient noteStoreClient;
		try {
			String accessTok = (String) session.getAttribute("accessToken");
			if (accessTok == null) {
				accessTok = doAuthenticate(request, callback);
			}

			EvernoteAuth evernoteAuthToRead = new EvernoteAuth(
					EVERNOTE_SERVICE, accessTok);
			noteStoreClient = new ClientFactory(evernoteAuthToRead)
			.createNoteStoreClient();
		} catch (EDAMUserException e4) {
			LOG.info("error occured", e4);
			throw new EvernoteApiException(e4.getMessage());
		} catch (EDAMSystemException e4) {
			LOG.info("error occured", e4);
			throw new EvernoteApiException(e4.getMessage());
		} catch (TException e4) {
			LOG.info("error occured", e4);
			throw new EvernoteApiException(e4.getMessage());
		}
		return noteStoreClient;
	}



	/**
	 * <h1 style="color:blue"><--To  normalization of strings--></h1>
	 * @param data
	 * @author 676877
	 * @return normalization String
	 * @since  3 August 2015
	 */
	public String normalizeString(String data){
		LOG.info("into normalizeString() Method...");

		String varNormalizeString = data;
		if(data==null){
			return data;
		}
		else{
		
			varNormalizeString = varNormalizeString.replaceAll("eval\\((.*)\\)", "");
			varNormalizeString = varNormalizeString.replaceAll("[\\\"\\\'][\\s]*javascript:(.*)[\\\"\\\']", "\"\"");
			varNormalizeString = varNormalizeString.replaceAll("\\[", "").replaceAll("\\]","");
			varNormalizeString = varNormalizeString.replaceAll("\\{", "").replaceAll("\\}","");
			varNormalizeString = varNormalizeString.replaceAll("\\(", "").replaceAll("\\)","");
			varNormalizeString = varNormalizeString.replaceAll("`", "");
			varNormalizeString = varNormalizeString.replaceAll("~", "");
			varNormalizeString = varNormalizeString.replaceAll("!", "");
			varNormalizeString = varNormalizeString.replaceAll("@", "");
			varNormalizeString = varNormalizeString.replaceAll("\\^", "");
			varNormalizeString = varNormalizeString.replaceAll("&", "");
			varNormalizeString = varNormalizeString.replaceAll("\\;", "");
			varNormalizeString = varNormalizeString.replaceAll("<", "").replaceAll(">","");
			LOG.info("after normalizeString() Method...");
			return varNormalizeString.replaceAll("%", "");
		}
	
	}
}
