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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitPushTest extends GitTest {

	@BeforeClass
	public static void prepareSsh() {
		readSshProperties();
	}

	@Test
	public void testPushNoBody() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		String remoteBranchLocation = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// push with no body
		request = getPostGitRemoteRequest(remoteBranchLocation, null, false, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testPushHead() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		IPath clonePath1 = new Path("file").append(project1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetFilesRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());

		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.optString(GitConstants.KEY_HEAD);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath2);

		// get project2 metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

		// clone1: list remotes
		request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));

		// clone1: change
		request = getPutFileRequest(projectId1 + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// clone2: get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: fetch
		fetch(remoteBranchLocation2);

		// clone2: get remote details
		JSONObject remoteBranch2 = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String newRefId2 = remoteBranch2.getString(ProtocolConstants.KEY_ID);

		// clone2: merge into HEAD, "git merge origin/master"
		gitHeadUri2 = remoteBranch2.getString(GitConstants.KEY_HEAD);
		JSONObject merge = merge(gitHeadUri2, newRefId2);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAST_FORWARD, mergeResult);

		// clone2: assert change from clone1 is in place
		request = getGetFilesRequest(projectId2 + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

	@Test
	public void testPushHeadSshWithPrivateKeyPassphrase() throws Exception {
		Assume.assumeTrue(sshRepo2 != null);
		Assume.assumeTrue(knownHosts2 != null);
		Assume.assumeTrue(privateKey != null);
		Assume.assumeTrue(passphrase != null);

		URI workspaceLocation = createWorkspace(getMethodName());
		URIish uri = new URIish(sshRepo2);

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(projectId1).makeAbsolute();
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setFilePath(clonePath).setKnownHosts(knownHosts2).setPrivateKey(privateKey).setPublicKey(publicKey).setPassphrase(passphrase).getWebRequest();
		String cloneContentLocation1 = clone(request);

		// clone1: get project/folder metadata
		request = getGetFilesRequest(cloneContentLocation1);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());

		// clone1: get git links
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.optString(GitConstants.KEY_HEAD);

		// clone2: create
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		clonePath = new Path("file").append(projectId2).makeAbsolute();
		request = new PostGitCloneRequest().setURIish(uri).setFilePath(clonePath).setKnownHosts(knownHosts2).setPrivateKey(privateKey).setPublicKey(publicKey).setPassphrase(passphrase).getWebRequest();
		String cloneContentLocation2 = clone(request);

		// clone2: get project/folder metadata
		request = getGetFilesRequest(cloneContentLocation2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());

		// clone2: get git links
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitCommitUri2 = gitSection2.getString(GitConstants.KEY_COMMIT);

		// clone1: list remotes
		request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));

		// clone1: change
		request = getPutFileRequest(projectId1 + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false, null, knownHosts2, privateKey, publicKey, passphrase, true);
		assertEquals(true, pushStatus.isOK());

		// clone2: get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: fetch
		fetch(remoteBranchLocation2, null, knownHosts2, privateKey, publicKey, passphrase, true);

		// clone2: get remote details
		JSONObject remoteBranch2 = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String newRefId2 = remoteBranch2.getString(ProtocolConstants.KEY_ID);

		// clone2: merge into HEAD, "git merge origin/master"
		gitCommitUri2 = remoteBranch2.getString(GitConstants.KEY_HEAD);
		JSONObject merge = merge(gitCommitUri2, newRefId2);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAST_FORWARD, mergeResult);

		// clone2: assert change from clone1 is in place
		request = getGetFilesRequest(projectId2 + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

	@Test
	public void testPushBranch() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		IPath clonePath1 = new Path("file").append(project1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone1 = clone(clonePath1);
		String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

		// get project1 metadata
		WebRequest request = getGetFilesRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

		// clone1: branch 'a'
		response = branch(branchesLocation1, "a");
		JSONObject branch = new JSONObject(response.getText());

		checkoutBranch(cloneLocation1, "a");

		// clone1: change
		request = getPutFileRequest(projectId1 + "/test.txt", "branch 'a' change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming branch 'a' commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push by remote branch
		String remoteBranchLocation = branch.getString(GitConstants.KEY_REMOTE);
		ServerStatus pushStatus = push(remoteBranchLocation, Constants.HEAD, false);
		assertTrue(pushStatus.isOK());

		// clone1: get the remote branch name
		request = getGetRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		JSONObject remoteBranch1 = new JSONObject(response.getText());
		String remoteBranchName1 = remoteBranch1.getString(ProtocolConstants.KEY_NAME);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone2 = clone(clonePath2);
		String cloneLocation2 = clone2.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation2 = clone2.getString(GitConstants.KEY_BRANCH);

		// get project2 metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);

		// create a local branch 'a' tracking remoteBranchName1 and checkout 'a'
		response = branch(branchesLocation2, "a", remoteBranchName1);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		response = checkoutBranch(cloneLocation2, "a");
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetFilesRequest(projectId2 + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("branch 'a' change", response.getText());
	}

	@Test
	@Ignore("not implemented yet")
	public void testPushNoSrc() {
		// TODO:
		// clone1
		// clone2
		// clone1: add, commit, try to push with empty Src, DELETE should be suggested
	}

	@Test
	public void testPushToDelete() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop1 = createProjectOrLink(workspaceLocation, getMethodName() + "-top1", null);
		IPath clonePathTop1 = new Path("file").append(projectTop1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectTop2 = createProjectOrLink(workspaceLocation, getMethodName() + "-top2", null);
		IPath clonePathTop2 = new Path("file").append(projectTop2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder1 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder1", null);
		IPath clonePathFolder1 = new Path("file").append(projectFolder1.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		JSONObject projectFolder2 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder2", null);
		IPath clonePathFolder2 = new Path("file").append(projectFolder2.getString(ProtocolConstants.KEY_ID)).append("folder2").makeAbsolute();

		JSONObject projectTop3 = createProjectOrLink(workspaceLocation, getMethodName() + "-top3", null);
		IPath clonePathTop3 = new Path("file").append(projectTop3.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder3 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder3", null);
		IPath clonePathFolder3 = new Path("file").append(projectFolder3.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		IPath[] clonePathsTop = new IPath[] {clonePathTop1, clonePathTop2};
		IPath[] clonePathsFolder = new IPath[] {clonePathFolder1, clonePathFolder2};
		IPath[] clonePathsMixed = new IPath[] {clonePathTop3, clonePathFolder3};
		IPath[][] clonePaths = new IPath[][] {clonePathsTop, clonePathsFolder, clonePathsMixed};

		for (IPath[] clonePath : clonePaths) {
			// clone 1
			JSONObject clone1 = clone(clonePath[0]);
			String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
			String contentLocation1 = clone1.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

			// clone 1 - get project1 metadata
			WebRequest request = getGetFilesRequest(contentLocation1);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project1 = new JSONObject(response.getText());
			String projectLocation1 = project1.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
			String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

			// clone 1 - create branch "a"
			response = branch(branchesLocation1, "a");
			JSONObject newBranch = new JSONObject(response.getText());
			String remoteBranchLocation1 = newBranch.getString(GitConstants.KEY_REMOTE);

			// clone 1 - checkout "a"
			final String newBranchName = "a";
			response = checkoutBranch(cloneLocation1, newBranchName);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - change
			request = getPutFileRequest(projectLocation1 + "/test.txt", "clone1 change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - push "a"
			ServerStatus pushStatus = push(remoteBranchLocation1, Constants.HEAD, false);
			assertEquals(true, pushStatus.isOK());

			// clone 1 - list remote branches - expect 2
			JSONObject remote1 = getRemote(gitRemoteUri1, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation1 = remote1.getString(ProtocolConstants.KEY_LOCATION);

			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote1 = new JSONObject(response.getText());
			JSONArray refsArray = remote1.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			JSONObject ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// clone 2 
			JSONObject clone2 = clone(clonePath[1]);
			String cloneLocation2 = clone2.getString(ProtocolConstants.KEY_LOCATION);
			String contentLocation2 = clone2.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// clone 2 - get project2 metadata
			request = getGetFilesRequest(contentLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project2 = new JSONObject(response.getText());
			JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);

			// clone 2 - check if the branch "a" is available
			JSONObject remote2 = getRemote(gitRemoteUri2, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation2 = remote2.getString(ProtocolConstants.KEY_LOCATION);

			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote2 = new JSONObject(response.getText());
			refsArray = remote2.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			String remoteBranchLocation2 = ref.getString(ProtocolConstants.KEY_LOCATION);

			// clone 2 - checkout branch "a"
			response = checkoutBranch(cloneLocation2, newBranchName);

			// clone 1 - delete remote branch "a"
			push(remoteBranchLocation1, "", false, false);

			// clone 1 - list remote branches - expect 1
			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote1 = new JSONObject(response.getText());
			refsArray = remote1.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(1, refsArray.length());
			ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// clone 2 - fetch
			request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation2, true, false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
			String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
			assertNotNull(taskLocation);
			String location = waitForTaskCompletion(taskLocation);

			// clone 2 - fetch task should fail
			request = getGetRequest(location);
			response = webConversation.getResponse(request);
			JSONObject status = new JSONObject(response.getText());
			assertFalse(status.getBoolean("Running"));
			assertEquals("Error", status.getJSONObject("Result").getString("Severity"));
		}
	}

	@Test
	public void testPushFromLog() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// log
		request = GitCommitTest.getGetGitCommitRequest(gitHeadUri, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject logResponse = new JSONObject(response.getText());
		JSONArray commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, commitsArray.length());

		// change
		request = getPutFileRequest(projectId + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// log again
		request = GitCommitTest.getGetGitCommitRequest(gitHeadUri, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		logResponse = new JSONObject(response.getText());
		commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(2, commitsArray.length());

		String remoteBranchLocation = logResponse.getString(GitConstants.KEY_REMOTE);

		// push
		request = getPostGitRemoteRequest(remoteBranchLocation, Constants.HEAD, false, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
	}

	@Test
	public void testPushRejected() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		IPath clonePath1 = new Path("file").append(project1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetFilesRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.optString(GitConstants.KEY_HEAD);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath2);

		// get project2 metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

		// clone1: change
		request = getPutFileRequest(projectId1 + "/test.txt", "clone1 change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// clone2: change
		request = getPutFileRequest(projectId2 + "/test.txt", "clone2 change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "clone2 change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(IStatus.WARNING, pushStatus.getSeverity());
		Status pushResult = Status.valueOf(pushStatus.getMessage());
		assertEquals(Status.REJECTED_NONFASTFORWARD, pushResult);
	}

	@Test
	public void testForcedPush() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop1 = createProjectOrLink(workspaceLocation, getMethodName() + "-top1", null);
		IPath clonePathTop1 = new Path("file").append(projectTop1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectTop2 = createProjectOrLink(workspaceLocation, getMethodName() + "-top2", null);
		IPath clonePathTop2 = new Path("file").append(projectTop2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder1 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder1", null);
		IPath clonePathFolder1 = new Path("file").append(projectFolder1.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		JSONObject projectFolder2 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder2", null);
		IPath clonePathFolder2 = new Path("file").append(projectFolder2.getString(ProtocolConstants.KEY_ID)).append("folder2").makeAbsolute();

		JSONObject projectTop3 = createProjectOrLink(workspaceLocation, getMethodName() + "-top3", null);
		IPath clonePathTop3 = new Path("file").append(projectTop3.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder3 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder3", null);
		IPath clonePathFolder3 = new Path("file").append(projectFolder3.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		IPath[] clonePathsTop = new IPath[] {clonePathTop1, clonePathTop2};
		IPath[] clonePathsFolder = new IPath[] {clonePathFolder1, clonePathFolder2};
		IPath[] clonePathsMixed = new IPath[] {clonePathTop3, clonePathFolder3};
		IPath[][] clonePaths = new IPath[][] {clonePathsTop, clonePathsFolder, clonePathsMixed};

		for (IPath[] clonePath : clonePaths) {
			// clone1
			String contentLocation1 = clone(clonePath[0]).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project1 metadata
			WebRequest request = getGetFilesRequest(contentLocation1);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project1 = new JSONObject(response.getText());
			String project1Location = project1.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
			String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

			// clone2
			String contentLocation2 = clone(clonePath[1]).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project2 metadata
			request = getGetFilesRequest(contentLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project2 = new JSONObject(response.getText());
			String project2Location = project2.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
			String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

			// clone1: change
			request = getPutFileRequest(project1Location + "/test.txt", "clone1 change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone1: add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone1: commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone1: push
			ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false);
			assertEquals(true, pushStatus.isOK());

			// clone2: change
			request = getPutFileRequest(project2Location + "/test.txt", "clone2 change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "clone2 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: push
			pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
			assertEquals(IStatus.WARNING, pushStatus.getSeverity());
			Status pushResult = Status.valueOf(pushStatus.getMessage());
			assertEquals(Status.REJECTED_NONFASTFORWARD, pushResult);

			// clone2: forced push
			pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false, true);
			assertEquals(true, pushStatus.isOK());
		}
	}

	@Test
	public void testPushTags() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		IPath clonePath1 = new Path("file").append(project1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetFilesRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());

		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitTagUri1 = gitSection1.optString(GitConstants.KEY_TAG);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath2);

		// get project2 metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());

		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);
		String gitTagUri2 = gitSection2.getString(GitConstants.KEY_TAG);

		// clone1: tag HEAD with 'tag'
		tag(gitTagUri1, "tag", Constants.HEAD);

		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, true);
		assertEquals(true, pushStatus.isOK());

		// clone2: list tags
		JSONArray tags = listTags(gitTagUri2);
		assertEquals(0, tags.length());

		// clone2: fetch + merge
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);
		remoteBranch = fetch(remoteBranchLocation2);
		String id = remoteBranch.getString(ProtocolConstants.KEY_ID);
		merge(gitHeadUri2, id);

		// clone2: list tags again
		tags = listTags(gitTagUri2);
		assertEquals(1, tags.length());
	}

	@Test
	public void testPushRemote() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop1 = createProjectOrLink(workspaceLocation, getMethodName() + "-top1", null);
		IPath clonePathTop1 = new Path("file").append(projectTop1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectTop2 = createProjectOrLink(workspaceLocation, getMethodName() + "-top2", null);
		IPath clonePathTop2 = new Path("file").append(projectTop2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder1 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder1", null);
		IPath clonePathFolder1 = new Path("file").append(projectFolder1.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		JSONObject projectFolder2 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder2", null);
		IPath clonePathFolder2 = new Path("file").append(projectFolder2.getString(ProtocolConstants.KEY_ID)).append("folder2").makeAbsolute();

		JSONObject projectTop3 = createProjectOrLink(workspaceLocation, getMethodName() + "-top3", null);
		IPath clonePathTop3 = new Path("file").append(projectTop3.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder3 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder3", null);
		IPath clonePathFolder3 = new Path("file").append(projectFolder3.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		IPath[] clonePathsTop = new IPath[] {clonePathTop1, clonePathTop2};
		IPath[] clonePathsFolder = new IPath[] {clonePathFolder1, clonePathFolder2};
		IPath[] clonePathsMixed = new IPath[] {clonePathTop3, clonePathFolder3};
		IPath[][] clonePaths = new IPath[][] {clonePathsTop, clonePathsFolder, clonePathsMixed};

		for (IPath[] clonePath : clonePaths) {
			// clone 1
			JSONObject clone1 = clone(clonePath[0]);
			String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
			String contentLocation1 = clone1.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

			// clone 1 - get project1 metadata
			WebRequest request = getGetFilesRequest(contentLocation1);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project1 = new JSONObject(response.getText());
			String projectLocation1 = project1.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
			String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

			// clone 1 - create branch "a"
			final String newBranchName = "a";
			response = branch(branchesLocation1, newBranchName);

			// clone 1 - checkout "a"
			response = checkoutBranch(cloneLocation1, newBranchName);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - change
			request = getPutFileRequest(projectLocation1 + "/test.txt", "clone1 change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - push origin 
			// HEAD should be used as refSpec, as no remote branch or refSpec in config is specified  
			JSONObject remote1 = getRemote(gitRemoteUri1, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation1 = remote1.getString(ProtocolConstants.KEY_LOCATION);

			ServerStatus pushStatus = push(remoteLocation1, null, false);
			assertEquals(true, pushStatus.isOK());

			// clone 1 - list remote branches - expect 2
			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote1 = new JSONObject(response.getText());
			JSONArray refsArray = remote1.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			JSONObject ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// clone 2 
			JSONObject clone2 = clone(clonePath[1]);
			String contentLocation2 = clone2.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// clone 2 - get project2 metadata
			request = getGetFilesRequest(contentLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project2 = new JSONObject(response.getText());
			JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);

			// clone 2 - check if the branch "a" is available
			JSONObject remote2 = getRemote(gitRemoteUri2, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation2 = remote2.getString(ProtocolConstants.KEY_LOCATION);

			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote2 = new JSONObject(response.getText());
			refsArray = remote2.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// cleanup before next iteration
			// as t repo is used as remote for test projects
			Git git = new Git(db);
			git.branchDelete().setBranchNames(newBranchName).setForce(true).call();
		}
	}

	@Test
	public void testPushRemoteAndUseConfigRefSpec() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop1 = createProjectOrLink(workspaceLocation, getMethodName() + "-top1", null);
		IPath clonePathTop1 = new Path("file").append(projectTop1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectTop2 = createProjectOrLink(workspaceLocation, getMethodName() + "-top2", null);
		IPath clonePathTop2 = new Path("file").append(projectTop2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder1 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder1", null);
		IPath clonePathFolder1 = new Path("file").append(projectFolder1.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		JSONObject projectFolder2 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder2", null);
		IPath clonePathFolder2 = new Path("file").append(projectFolder2.getString(ProtocolConstants.KEY_ID)).append("folder2").makeAbsolute();

		JSONObject projectTop3 = createProjectOrLink(workspaceLocation, getMethodName() + "-top3", null);
		IPath clonePathTop3 = new Path("file").append(projectTop3.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder3 = createProjectOrLink(workspaceLocation, getMethodName() + "-folder3", null);
		IPath clonePathFolder3 = new Path("file").append(projectFolder3.getString(ProtocolConstants.KEY_ID)).append("folder1").makeAbsolute();

		IPath[] clonePathsTop = new IPath[] {clonePathTop1, clonePathTop2};
		IPath[] clonePathsFolder = new IPath[] {clonePathFolder1, clonePathFolder2};
		IPath[] clonePathsMixed = new IPath[] {clonePathTop3, clonePathFolder3};
		IPath[][] clonePaths = new IPath[][] {clonePathsTop, clonePathsFolder, clonePathsMixed};

		for (IPath[] clonePath : clonePaths) {
			// clone 1
			JSONObject clone1 = clone(clonePath[0]);
			String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
			String contentLocation1 = clone1.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

			// clone 1 - get project1 metadata
			WebRequest request = getGetFilesRequest(contentLocation1);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project1 = new JSONObject(response.getText());
			String projectLocation1 = project1.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
			String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);
			String gitConfigUri1 = gitSection1.getString(GitConstants.KEY_CONFIG);

			// clone 1 - create branch "a"
			final String newBranchName1 = "a";
			response = branch(branchesLocation1, newBranchName1);

			// clone 1 - checkout "a"
			response = checkoutBranch(cloneLocation1, newBranchName1);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - change
			request = getPutFileRequest(projectLocation1 + "/test.txt", "clone1 change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - set remote.origin.push to "refs/heads/a:refs/heads/b" 
			// to force name mapping from "a" to "b"
			final String newBranchName2 = "b";
			String pushRefSpec = "refs/heads/" + newBranchName1 + ":refs/heads/" + newBranchName2;
			request = GitConfigTest.getPostGitConfigRequest(gitConfigUri1, "remote.origin.push", pushRefSpec);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			// clone 1 - push origin   
			// refSpec from config should be used, as it was specified a moment ago
			JSONObject remote1 = getRemote(gitRemoteUri1, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation1 = remote1.getString(ProtocolConstants.KEY_LOCATION);

			ServerStatus pushStatus = push(remoteLocation1, null, false);
			assertEquals(true, pushStatus.isOK());

			// clone 1 - list remote branches - expect 2
			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote1 = new JSONObject(response.getText());
			JSONArray refsArray = remote1.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			JSONObject ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName2, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// clone 2 
			JSONObject clone2 = clone(clonePath[1]);
			String contentLocation2 = clone2.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// clone 2 - get project2 metadata
			request = getGetFilesRequest(contentLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project2 = new JSONObject(response.getText());
			JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);

			// clone 2 - check if the branch "b" is available
			JSONObject remote2 = getRemote(gitRemoteUri2, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation2 = remote2.getString(ProtocolConstants.KEY_LOCATION);

			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote2 = new JSONObject(response.getText());
			refsArray = remote2.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName2, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// cleanup before next iteration
			// as t repo is used as remote for test projects
			Git git = new Git(db);
			git.branchDelete().setBranchNames(newBranchName2).setForce(true).call();
		}
	}

	static WebRequest getPostGitRemoteRequest(String location, String srcRef, boolean tags, boolean force) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.REMOTE_RESOURCE + location;

		JSONObject body = new JSONObject();
		if (srcRef != null)
			body.put(GitConstants.KEY_PUSH_SRC_REF, srcRef);
		body.put(GitConstants.KEY_PUSH_TAGS, tags);
		body.put(GitConstants.KEY_FORCE, force);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPostGitRemoteRequest(String location, String srcRef, boolean tags, boolean force, String name, String kh, byte[] privk, byte[] pubk, byte[] p) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.REMOTE_RESOURCE + location;

		JSONObject body = new JSONObject();

		body.put(ProtocolConstants.KEY_NAME, name);
		if (kh != null)
			body.put(GitConstants.KEY_KNOWN_HOSTS, kh);
		if (privk != null)
			body.put(GitConstants.KEY_PRIVATE_KEY, new String(privk));
		if (pubk != null)
			body.put(GitConstants.KEY_PUBLIC_KEY, new String(pubk));
		if (p != null)
			body.put(GitConstants.KEY_PASSPHRASE, new String(p));

		if (srcRef != null)
			body.put(GitConstants.KEY_PUSH_SRC_REF, srcRef);
		body.put(GitConstants.KEY_PUSH_TAGS, tags);
		body.put(GitConstants.KEY_FORCE, force);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
