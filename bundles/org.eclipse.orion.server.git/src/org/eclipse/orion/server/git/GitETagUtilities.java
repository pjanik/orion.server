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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.core.HashUtilities;

public class GitETagUtilities {

	/**
	 * Returns an ETag identifier assigned to a specific version of the Git status page. 
	 * An identifier is based on the Git index file and the commit history.
	 * @param db Git repository
	 * @return ETag string representation
	 * @throws CoreException
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws JGitInternalException 
	 * @throws NoHeadException 
	 */
	public static String generateStatusETag(Repository db) throws CoreException, IOException, NoSuchAlgorithmException, NoHeadException, JGitInternalException {
		Git git = new Git(db);
		Status status = git.status().call();

		LinkedList<String> indexList = new LinkedList<String>();
		indexList.addAll(status.getAdded());
		indexList.addAll(status.getChanged());
		indexList.addAll(status.getRemoved());

		StringBuilder sb = new StringBuilder();
		for (String file : indexList) {
			sb.append(file);
		}
		// append commits log ETag
		sb.append(generateCommitsETag(db));

		return HashUtilities.getHash(sb.toString(), HashUtilities.SHA_1);
	}

	/**
	 * Returns an ETag identifier assigned to a specific version of the Git commit history.
	 * @param db Git repository
	 * @return ETag string representation
	 * @throws CoreException
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws JGitInternalException 
	 * @throws NoHeadException 
	 */
	public static String generateCommitsETag(Repository db) throws CoreException, IOException, NoSuchAlgorithmException, NoHeadException, JGitInternalException {
		Git git = new Git(db);
		Iterable<RevCommit> commits = git.log().call();

		StringBuilder sb = new StringBuilder();
		for (RevCommit commit : commits) {
			sb.append(commit.getName());
		}

		return HashUtilities.getHash(sb.toString(), HashUtilities.SHA_1);
	}
}
