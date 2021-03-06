/**
 * The MIT License
 * Copyright (c) 2013 Pierre-Denis Vanduynslager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.syncthemall.sample;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeTypeException;

import com.ctc.wstx.stax.WstxOutputFactory;
import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteSortOrder;
import com.evernote.edam.type.Resource;
import com.evernote.edam.userstore.Constants;
import com.evernote.edam.userstore.UserStore;
import com.evernote.thrift.TException;
import com.evernote.thrift.protocol.TBinaryProtocol;
import com.evernote.thrift.transport.THttpClient;
import com.evernote.thrift.transport.TTransportException;
import com.syncthemall.enml4j.ENMLProcessor;

public class ENML4jDemo {

	/****************************************************************************
	 * You must change the following values before running this sample code     *
	 ****************************************************************************/

	// Real applications authenticate with Evernote using OAuth, but for the
	// purpose of exploring the API, you can get a developer token that allows
	// you to access your own Evernote account. To get a developer token, visit
	// https://www.evernote.com/api/DeveloperToken.action
	private static final String authToken = "your developer token";

	/****************************************************************************
	 * You must change the following values before running this sample code     *
	 ****************************************************************************/
	private static final String downloadFolder = "/path/to/folder";
	
	/****************************************************************************
	 * You shouldn't need to change anything below here to run this sample code *
	 ****************************************************************************/
	private static final String evernoteHost = "www.evernote.com";
	private static final String userStoreUrl = "https://" + evernoteHost + "/edam/user";
	private static final String userAgent = "Evernote/ENML4j (Java) " + Constants.EDAM_VERSION_MAJOR + "."
			+ Constants.EDAM_VERSION_MINOR;
	private static NoteStore.Client noteStore;

	// Used in this demo as a convenient way to map file extension to mime type
	private static TikaConfig config = TikaConfig.getDefaultConfig();

	private static ENMLProcessor ENMLProcessor;

	public ENML4jDemo() {

		// Uses Woodstox has a the stAX implementation
		System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory");
		System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory");
		System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory");

		// Creates an ENMLProcessor with default converters
		ENMLProcessor = new ENMLProcessor();

		// Set the property P_AUTOMATIC_EMPTY_ELEMENTS to false to write HTML with explicit closing tags to works better
		// with browser.
		((WstxOutputFactory) ENMLProcessor.getOutputFactory()).setProperty(
				WstxOutputFactory.P_AUTOMATIC_EMPTY_ELEMENTS, false);
	}


	public static void main(String[] args) throws Exception {
		if ("your developer token".equals(authToken)) {
			System.err.println("Please fill in your developer token");
			System.err
					.println("To get a developer token, go to https://www.evernote.com/api/DeveloperToken.action");
			return;
		}
		if ("/path/to/folder".equals(downloadFolder)) {
			System.err.println("Please fill the download folder path");
			return;
		}

		ENML4jDemo demo = new ENML4jDemo();

		if (demo.intitializeEvernote()) {
			try {
				// List 10 notes from Evernote
				List<Note> notes = demo.listNotes();

				for (Note note : notes) {
					// Create a folder in the download folder to save the HTML file generated by ENML4j
					File noteDirectory = new File(downloadFolder + "/" + note.getTitle());
					noteDirectory.mkdir();

					// Get the Note with it's content and resources data
					note = noteStore.getNote(authToken, note.getGuid(), true, true, false, false);

					// Save the Note attachments (the binary files) in the download folder
					saveAttachement(note);

					// Creates the mapping between the binary files URL and the Resources GUID
					Map<String, String> mapHashtoURL = new HashMap<String, String>();
					if (note.getResources() != null) {
						for (Resource resource : note.getResources()) {
							String attachementPath = "./" + resource.getGuid()
									+ config.getMimeRepository().forName(resource.getMime()).getExtension();
							mapHashtoURL.put(resource.getGuid(), attachementPath);
						}
					}
					// ///////////////////////////////////////////////////////////////
					// Option 1 : creates an HTML file referencing the binary files //
					// ///////////////////////////////////////////////////////////////
					FileOutputStream fos = new FileOutputStream(noteDirectory + "/" + note.getTitle() + ".html");
					ENMLProcessor.noteToHTML(note, mapHashtoURL, fos);

					// ///////////////////////////////////////////////////////////
					// Option 2 : creates an HTML file with inline binary files //
					// ///////////////////////////////////////////////////////////
					FileOutputStream fos2 = new FileOutputStream(noteDirectory + "/" + note.getTitle() + "-inline.html");
					ENMLProcessor.noteToInlineHTML(note, fos2);

				}

			} catch (EDAMUserException e) {
				// These are the most common error types that you'll need to
				// handle
				// EDAMUserException is thrown when an API call fails because a
				// paramter was invalid.
				if (e.getErrorCode() == EDAMErrorCode.AUTH_EXPIRED) {
					System.err.println("Your authentication token is expired!");
				} else if (e.getErrorCode() == EDAMErrorCode.INVALID_AUTH) {
					System.err.println("Your authentication token is invalid!");
				} else if (e.getErrorCode() == EDAMErrorCode.QUOTA_REACHED) {
					System.err.println("Your authentication token is invalid!");
				} else {
					System.err.println("Error: " + e.getErrorCode().toString() + " parameter: " + e.getParameter());
				}
			} catch (EDAMSystemException e) {
				System.err.println("System error: " + e.getErrorCode().toString());
			} catch (TTransportException t) {
				System.err.println("Networking error: " + t.getMessage());
			}

		}
	}

	/**
	 * Save the binary content of Notes Resources as Files in the download folder. The HTML file generated by ENML4j
	 * will references this files. This methods is used only a a demo. ENML4j can creates HTML files referencing any by
	 * URL (on a web server, FTP server, file hosting service etc...
	 */
	private static void saveAttachement(Note note) throws EDAMUserException, EDAMSystemException,
			EDAMNotFoundException, TException, IOException, MimeTypeException {

		File noteDirectory = new File(downloadFolder + "/" + note.getTitle());
		noteDirectory.mkdir();
		note = noteStore.getNote(authToken, note.getGuid(), true, true, false, false);
		if (note.getResources() != null) {
			for (Resource resource : note.getResources()) {
				String attachementPath = noteDirectory + "/" + resource.getGuid()
						+ config.getMimeRepository().forName(resource.getMime()).getExtension();
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(attachementPath));
				bos.write(resource.getData().getBody());
				bos.flush();
				bos.close();
			}
		}
	}

	/**
	 * List some Notes. See https://github.com/evernote/evernote-sdk-java/blob/master/sample/client/EDAMDemo.java
	 */
	private List<Note> listNotes() throws Exception {
		// List the notes in the user's account
		System.out.println("Listing notes:");

		// Next, search for the first 20 notes in this notebook, ordering by
		// creation date
		NoteFilter filter = new NoteFilter();
		filter.setOrder(NoteSortOrder.CREATED.getValue());
		filter.setAscending(true);

		NoteList noteList = noteStore.findNotes(authToken, filter, 0, 20);
		List<Note> notes = noteList.getNotes();
		for (Note note : notes) {
			System.out.println(" * " + note.getTitle());
		}
		return notes;
	}

	/**
	 * Initialize the noteStrore. See
	 * https://github.com/evernote/evernote-sdk-java/blob/master/sample/client/EDAMDemo.java
	 */
	private boolean intitializeEvernote() throws Exception {
		// Set up the UserStore client and check that we can speak to the server
		THttpClient userStoreTrans = new THttpClient(userStoreUrl);
		userStoreTrans.setCustomHeader("User-Agent", userAgent);
		TBinaryProtocol userStoreProt = new TBinaryProtocol(userStoreTrans);
		UserStore.Client userStore = new UserStore.Client(userStoreProt, userStoreProt);

		boolean versionOk = userStore.checkVersion("Evernote EDAMDemo (Java)",
				com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
				com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);
		if (!versionOk) {
			System.err.println("Incomatible Evernote client protocol version");
			return false;
		}

		// Get the URL used to interact with the contents of the user's account
		// When your application authenticates using OAuth, the NoteStore URL
		// will
		// be returned along with the auth token in the final OAuth request.
		// In that case, you don't need to make this call.
		String notestoreUrl = userStore.getNoteStoreUrl(authToken);

		// Set up the NoteStore client
		THttpClient noteStoreTrans = new THttpClient(notestoreUrl);
		noteStoreTrans.setCustomHeader("User-Agent", userAgent);
		TBinaryProtocol noteStoreProt = new TBinaryProtocol(noteStoreTrans);
		noteStore = new NoteStore.Client(noteStoreProt, noteStoreProt);

		return true;
	}

}
