/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitBranchTest extends GitTest {
	@Test
	public void testListBranches() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		JSONObject clone = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// list branches
		WebRequest request = getGetRequest(branchesLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject branches = new JSONObject(response.getText());
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, branchesArray.length());

		// validate branch metadata
		JSONObject branch = branchesArray.getJSONObject(0);
		assertEquals(Constants.MASTER, branch.getString(ProtocolConstants.KEY_NAME));
		assertBranchUri(branch.getString(ProtocolConstants.KEY_LOCATION));
		assertTrue(branch.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));
		// that's it for now
	}

	@Test
	public void testAddRemoveBranch() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		JSONObject clone = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// create branch
		WebResponse response = branch(branchesLocation, "a");
		String branchLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		// check details
		WebRequest request = getGetRequest(branchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// list branches
		request = getGetRequest(branchesLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject branches = new JSONObject(response.getText());
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(2, branchesArray.length());
		JSONObject branch0 = branchesArray.getJSONObject(0);
		JSONObject branch1 = branchesArray.getJSONObject(1);
		if (branch0.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false))
			assertFalse(branch1.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));
		else
			assertTrue(branch1.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));

		// remove branch
		request = getDeleteGitBranchRequest(branchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// list branches again, make sure it's gone
		request = getGetRequest(branchesLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		branches = new JSONObject(response.getText());
		branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, branchesArray.length());
		JSONObject branch = branchesArray.getJSONObject(0);
		assertTrue(branch.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));
	}

	@Test
	public void testCreateTrackingBranch() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());

			String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE);

			// create local branch tracking origin/master
			final String BRANCH_NAME = "a";
			final String REMOTE_BRANCH = Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER;

			branch(branchesLocation, BRANCH_NAME, REMOTE_BRANCH);

			// modify
			request = getPutFileRequest(projectLocation + "test.txt", "some change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// push
			ServerStatus pushStatus = push(gitRemoteUri, 1, 0, Constants.MASTER, Constants.HEAD, false);
			assertEquals(true, pushStatus.isOK());

			// TODO: replace with RESTful API for git pull when available
			// try to pull - up to date status is expected
			Git git = new Git(getRepositoryForContentLocation(cloneContentLocation));
			PullResult pullResults = git.pull().call();
			assertEquals(Constants.DEFAULT_REMOTE_NAME, pullResults.getFetchedFrom());
			assertEquals(MergeStatus.ALREADY_UP_TO_DATE, pullResults.getMergeResult().getMergeStatus());
			assertNull(pullResults.getRebaseResult());

			// checkout branch which was created a moment ago
			response = checkoutBranch(cloneLocation, BRANCH_NAME);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// TODO: replace with RESTful API for git pull when available
			// try to pull again - now fast forward update is expected
			pullResults = git.pull().call();
			assertEquals(Constants.DEFAULT_REMOTE_NAME, pullResults.getFetchedFrom());
			assertEquals(MergeStatus.FAST_FORWARD, pullResults.getMergeResult().getMergeStatus());
			assertNull(pullResults.getRebaseResult());
		}
	}

	static JSONObject getCurrentBranch(JSONObject branches) throws JSONException {
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		for (int i = 0; i < branchesArray.length(); i++) {
			JSONObject branch = branchesArray.getJSONObject(i);
			if (branch.getBoolean(GitConstants.KEY_BRANCH_CURRENT))
				return branch;
		}
		return null;
	}

	private WebRequest getDeleteGitBranchRequest(String location) {
		String requestURI;
		if (location.startsWith("http://")) {
			requestURI = location;
		} else {
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.BRANCH_RESOURCE + location;
		}
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
