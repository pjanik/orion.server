/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

/**
 * Constants used by Git.
 */
public class GitConstants {

	public static final String DIFF_RESOURCE = "diff"; //$NON-NLS-1$

	public static final String STATUS_RESOURCE = "status"; //$NON-NLS-1$

	public static final String INDEX_RESOURCE = "index"; //$NON-NLS-1$

	public static final String CLONE_RESOURCE = "clone"; //$NON-NLS-1$

	public static final String COMMIT_RESOURCE = "commit"; //$NON-NLS-1$

	public static final String CONFIG_RESOURCE = "config"; //$NON-NLS-1$

	public static final String REMOTE_RESOURCE = "remote"; //$NON-NLS-1$

	public static final String BRANCH_RESOURCE = "branch"; //$NON-NLS-1$

	public static final String TAG_RESOURCE = "tag"; //$NON-NLS-1$

	public static final String KEY_GIT = "Git"; //$NON-NLS-1$

	public static final String KEY_NAME = "GitName"; //$NON-NLS-1$

	public static final String KEY_MAIL = "GitMail"; //$NON-NLS-1$

	public static final String KEY_DIFF = "DiffLocation"; //$NON-NLS-1$

	public static final String KEY_STATUS = "StatusLocation"; //$NON-NLS-1$

	public static final String KEY_INDEX = "IndexLocation"; //$NON-NLS-1$

	public static final String KEY_COMMIT = "CommitLocation"; //$NON-NLS-1$

	public static final String KEY_REMOTE = "RemoteLocation"; //$NON-NLS-1$

	public static final String KEY_DEFAULT_REMOTE_BRANCH = "DefaultRemoteBranchLocation"; //$NON-NLS-1$

	public static final String KEY_TAG = "TagLocation"; //$NON-NLS-1$

	public static final String KEY_HEAD = "HeadLocation"; //$NON-NLS-1$

	public static final String KEY_CLONE = "CloneLocation"; //$NON-NLS-1$

	public static final String KEY_CONFIG = "ConfigLocation"; //$NON-NLS-1$

	public static final String KEY_BRANCH = "BranchLocation"; //$NON-NLS-1$

	public static final String KEY_URL = "GitUrl"; //$NON-NLS-1$

	public static final String KEY_USERNAME = "GitSshUsername"; //$NON-NLS-1$

	public static final String KEY_PASSWORD = "GitSshPassword"; //$NON-NLS-1$

	public static final String KEY_KNOWN_HOSTS = "GitSshKnownHost"; //$NON-NLS-1$

	public static final String KEY_PRIVATE_KEY = "GitSshPrivateKey"; //$NON-NLS-1$

	public static final String KEY_PASSPHRASE = "GitSshPassphrase"; //$NON-NLS-1$

	public static final String KEY_PUBLIC_KEY = "GitSshPublicKey"; //$NON-NLS-1$

	public static final String KEY_STATUS_ADDED = "Added"; //$NON-NLS-1$

	public static final String KEY_STATUS_CHANGED = "Changed"; //$NON-NLS-1$

	public static final String KEY_STATUS_MISSING = "Missing"; //$NON-NLS-1$

	public static final String KEY_STATUS_MODIFIED = "Modified"; //$NON-NLS-1$

	public static final String KEY_STATUS_REMOVED = "Removed"; //$NON-NLS-1$

	public static final String KEY_STATUS_UNTRACKED = "Untracked"; //$NON-NLS-1$

	public static final String KEY_STATUS_CONFLICTING = "Conflicting"; //$NON-NLS-1$

	public static final String KEY_RESET_TYPE = "Reset"; //$NON-NLS-1$

	public static final String KEY_DIFF_DEFAULT = "Default"; //$NON-NLS-1$

	public static final String KEY_DIFF_CACHED = "Cached"; //$NON-NLS-1$

	public static final String KEY_COMMIT_NEW = "New"; //$NON-NLS-1$

	public static final String KEY_COMMIT_OLD = "Old"; //$NON-NLS-1$

	public static final String KEY_COMMIT_BASE = "Base"; //$NON-NLS-1$

	public static final String KEY_COMMIT_MESSAGE = "Message"; //$NON-NLS-1$

	public static final String KEY_COMMIT_AMEND = "Amend"; //$NON-NLS-1$

	public static final String KEY_COMMIT_TIME = "Time"; //$NON-NLS-1$

	public static final String KEY_COMMIT_DIFFS = "Diffs"; //$NON-NLS-1$

	public static final String KEY_COMMIT_DIFF_NEWPATH = "NewPath"; //$NON-NLS-1$

	public static final String KEY_COMMIT_DIFF_OLDPATH = "OldPath"; //$NON-NLS-1$

	public static final String KEY_COMMIT_DIFF_CHANGETYPE = "ChangeType"; //$NON-NLS-1$

	public static final String KEY_CONFIG_ENTRY_KEY = "Key"; //$NON-NLS-1$

	public static final String KEY_CONFIG_ENTRY_VALUE = "Value"; //$NON-NLS-1$

	public static final String KEY_BRANCH_NAME = "Branch"; //$NON-NLS-1$

	public static final String KEY_REMOTE_NAME = "Remote"; //$NON-NLS-1$

	public static final String KEY_REMOTE_URI = "RemoteURI"; //$NON-NLS-1$

	public static final String KEY_REMOTE_PUSH_URI = "PushURI"; //$NON-NLS-1$

	public static final String KEY_REMOTE_PUSH_REF = "PushRefSpec"; //$NON-NLS-1$

	public static final String KEY_REMOTE_FETCH_REF = "FetchRefSpec"; //$NON-NLS-1$

	public static final String KEY_BRANCH_CURRENT = "Current"; //$NON-NLS-1$

	public static final String KEY_AUTHOR_NAME = "AuthorName"; //$NON-NLS-1$

	public static final String KEY_AUTHOR_EMAIL = "AuthorEmail"; //$NON-NLS-1$

	public static final String KEY_COMMITTER_NAME = "CommitterName"; //$NON-NLS-1$

	public static final String KEY_COMMITTER_EMAIL = "CommitterEmail"; //$NON-NLS-1$

	public static final String KEY_FETCH = "Fetch"; //$NON-NLS-1$

	public static final String KEY_MERGE = "Merge"; //$NON-NLS-1$

	public static final String KEY_PUSH_SRC_REF = "PushSrcRef"; //$NON-NLS-1$

	public static final String KEY_PUSH_TAGS = "PushTags"; //$NON-NLS-1$

	public static final String KEY_RESULT = "Result"; //$NON-NLS-1$

	public static final String KEY_TAG_COMMIT = "Commit"; //$NON-NLS-1$

	public static final String REMOTE_TRACKING_BRANCH_TYPE = "RemoteTrackingBranch"; //$NON-NLS-1$

	public static final String DIFF_TYPE = "Diff"; //$NON-NLS-1$

	public static final String COMMIT_TYPE = "Commit"; //$NON-NLS-1$
}
