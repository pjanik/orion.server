/*******************************************************************************
 * Copyright (c)  2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.AdditionalRebaseStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitRebaseTest extends GitTest {
	@Test
	public void testRebaseSelf() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			// "git rebase master"
			JSONObject rebase = rebase(gitHeadUri, Constants.MASTER);
			RebaseResult.Status rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.UP_TO_DATE, rebaseResult);
		}
	}

	@Test
	public void testRebase() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);

			String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);

			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			// create branch 'a'
			branch(branchesLocation, "a");

			// checkout 'a'
			Repository db1 = getRepositoryForContentLocation(contentLocation);
			Git git = new Git(db1);
			assertBranchExist(git, "a");
			checkoutBranch(cloneLocation, "a");

			// modify while on 'a'
			request = getPutFileRequest(projectLocation + "/test.txt", "change in a");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit on a", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);

			// checkout 'master'
			checkoutBranch(cloneLocation, Constants.MASTER);

			// modify a different file on master
			request = getPutFileRequest(projectLocation + "/folder/folder.txt", "change in master");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
			gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);

			// rebase: "git rebase a"
			JSONObject rebase = rebase(gitHeadUri, "a");
			RebaseResult.Status rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.OK, rebaseResult);

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);

			request = getGetFilesRequest(projectLocation + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("change in a", response.getText());

			request = getGetFilesRequest(projectLocation + "/folder/folder.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("change in master", response.getText());
		}
	}

	@Test
	public void testRebaseStopOnConflictAndAbort() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);

			String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);

			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			// modify file while on 'master'
			request = getPutFileRequest(projectLocation + "/test.txt", "1\n2\n3");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "first commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// create branch 'a'
			branch(branchesLocation, "a");

			// modify file while on 'master'
			request = getPutFileRequest(projectLocation + "/test.txt", "1master\n2\n3");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "second commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// checkout 'a'
			Repository db1 = getRepositoryForContentLocation(contentLocation);
			Git git = new Git(db1);
			assertBranchExist(git, "a");
			checkoutBranch(cloneLocation, "a");

			// modify while on 'a' - conflicting change (first line) and non-conflicting (last line)
			request = getPutFileRequest(projectLocation + "/test.txt", "1a\n2\n3\n4a");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "first commit on a", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);

			// rebase: "git rebase master"
			JSONObject rebase = rebase(gitHeadUri, "master");
			RebaseResult.Status rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.STOPPED, rebaseResult);

			// check conflicting file
			request = getGetFilesRequest(projectLocation + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("<<<<<<< OURS\n1master\n=======\n1a\n>>>>>>> THEIRS\n2\n3\n4a\n", response.getText());

			// abort rebase
			rebase = rebase(gitHeadUri, Operation.ABORT);
			rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.ABORTED, rebaseResult);

			// file should reset to "a" branch
			request = getGetFilesRequest(projectLocation + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("1a\n2\n3\n4a", response.getText());

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);
		}
	}

	@Test
	public void testRebaseStopOnConflictAndContinue() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);

			String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);

			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			// modify file while on 'master'
			request = getPutFileRequest(projectLocation + "/test.txt", "1\n2\n3");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "first commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// create branch 'a'
			branch(branchesLocation, "a");

			// modify file while on 'master'
			request = getPutFileRequest(projectLocation + "/test.txt", "1master\n2\n3");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "second commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// checkout 'a'
			Repository db1 = getRepositoryForContentLocation(contentLocation);
			Git git = new Git(db1);
			assertBranchExist(git, "a");
			checkoutBranch(cloneLocation, "a");

			// modify while on 'a' - conflicting change (first line) and non-conflicting (last line)
			request = getPutFileRequest(projectLocation + "/test.txt", "1a\n2\n3\n4a");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "first commit on a", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);

			// rebase: "git rebase master"
			JSONObject rebase = rebase(gitHeadUri, "master");
			RebaseResult.Status rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.STOPPED, rebaseResult);

			// check conflicting file
			request = getGetFilesRequest(projectLocation + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("<<<<<<< OURS\n1master\n=======\n1a\n>>>>>>> THEIRS\n2\n3\n4a\n", response.getText());

			// continue rebase without conflict resolving
			rebase = rebase(gitHeadUri, Operation.CONTINUE);
			AdditionalRebaseStatus errRebaseResult = AdditionalRebaseStatus.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(AdditionalRebaseStatus.FAILED_UNMERGED_PATHS, errRebaseResult);

			// resolve conflict
			request = getPutFileRequest(projectLocation + "/test.txt", "1amaster\n2\n3\n4a");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// and add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// continue rebase
			rebase = rebase(gitHeadUri, Operation.CONTINUE);
			rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.OK, rebaseResult);

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);
		}
	}

	@Test
	public void testRebaseStopOnConflictAndSkipPatch() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);

			String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);

			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			// modify file while on 'master'
			request = getPutFileRequest(projectLocation + "/test.txt", "1\n2\n3");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "first commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// create branch 'a'
			branch(branchesLocation, "a");

			// modify file while on 'master'
			request = getPutFileRequest(projectLocation + "/test.txt", "1master\n2\n3");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "second commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// checkout 'a'
			Repository db1 = getRepositoryForContentLocation(contentLocation);
			Git git = new Git(db1);
			assertBranchExist(git, "a");
			checkoutBranch(cloneLocation, "a");

			// modify while on 'a' - conflicting change (first line) and non-conflicting (last line)
			request = getPutFileRequest(projectLocation + "/test.txt", "1a\n2\n3\n4a");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "first commit on a", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);

			// rebase: "git rebase master"
			JSONObject rebase = rebase(gitHeadUri, "master");
			RebaseResult.Status rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.STOPPED, rebaseResult);

			// check conflicting file
			request = getGetFilesRequest(projectLocation + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("<<<<<<< OURS\n1master\n=======\n1a\n>>>>>>> THEIRS\n2\n3\n4a\n", response.getText());

			// continue rebase without conflict resolving - error expected
			rebase = rebase(gitHeadUri, Operation.CONTINUE);
			AdditionalRebaseStatus errRebaseResult = AdditionalRebaseStatus.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(AdditionalRebaseStatus.FAILED_UNMERGED_PATHS, errRebaseResult);

			// continue rebase
			rebase = rebase(gitHeadUri, Operation.SKIP);
			rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.OK, rebaseResult);

			// file should reset to "master" branch
			request = getGetFilesRequest(projectLocation + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("1master\n2\n3", response.getText());

			// assert clean
			request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);
		}
	}

	@Test
	public void testRebaseInvalidOperation() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			JSONObject rebase = rebase(gitHeadUri, Operation.CONTINUE);
			AdditionalRebaseStatus errRebaseResult = AdditionalRebaseStatus.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(AdditionalRebaseStatus.FAILED_WRONG_REPOSITORY_STATE, errRebaseResult);

			rebase = rebase(gitHeadUri, Operation.ABORT);
			errRebaseResult = AdditionalRebaseStatus.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(AdditionalRebaseStatus.FAILED_WRONG_REPOSITORY_STATE, errRebaseResult);

			rebase = rebase(gitHeadUri, Operation.SKIP);
			errRebaseResult = AdditionalRebaseStatus.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(AdditionalRebaseStatus.FAILED_WRONG_REPOSITORY_STATE, errRebaseResult);
		}
	}

	@Test
	public void testRebaseOnRemote() throws Exception {
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

			// clone1: get remote details
			JSONObject details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
			String refId1 = details.getString(ProtocolConstants.KEY_ID);
			String remoteBranchLocation1 = details.getString(ProtocolConstants.KEY_LOCATION);

			// clone2: change
			request = getPutFileRequest(project2Location + "/test.txt", "incoming change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "incoming change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: push
			ServerStatus pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
			assertEquals(true, pushStatus.isOK());

			// clone1: fetch
			request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation1, true, false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
			String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
			assertNotNull(taskLocation);
			waitForTaskCompletion(taskLocation);

			// clone1: get remote details again
			JSONObject remoteBranch = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
			String newRefId1 = remoteBranch.getString(ProtocolConstants.KEY_ID);
			// an incoming commit
			assertFalse(refId1.equals(newRefId1));

			String gitHeadUri = remoteBranch.getString(GitConstants.KEY_HEAD);
			assertNotNull(gitHeadUri);

			// rebase
			JSONObject rebase = rebase(gitHeadUri, newRefId1);
			RebaseResult.Status rebaseResult = RebaseResult.Status.valueOf(rebase.getString(GitConstants.KEY_RESULT));
			assertEquals(RebaseResult.Status.FAST_FORWARD, rebaseResult);

			request = getGetFilesRequest(project1Location + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("incoming change", response.getText());
		}
	}

	public static WebRequest getPostGitRebaseRequest(String location, String commit, Operation operation) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_REBASE, commit);
		if (operation != null)
			body.put(GitConstants.KEY_OPERATION, operation.name());
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
