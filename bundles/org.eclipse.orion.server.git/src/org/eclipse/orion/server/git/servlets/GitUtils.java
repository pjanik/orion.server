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
package org.eclipse.orion.server.git.servlets;

import java.io.File;
import java.io.FileFilter;
import java.util.*;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;

public class GitUtils {

	public enum Traverse {
		GO_UP, GO_DOWN, CURRENT
	}

	/**
	 * Returns the file representing the Git repository directory for the given 
	 * file path or any of its parent in the filesystem. If the file doesn't exits,
	 * is not a Git repository or an error occurred while transforming the given
	 * path into a store <code>null</code> is returned.
	 *
	 * @param path expected format /file/{projectId}[/{path}]
	 * @return the .git folder if found or <code>null</code> the give path
	 * cannot be resolved to a file or it's not under control of a git repository
	 * @throws CoreException
	 */
	public static File getGitDir(IPath path) throws CoreException {
		Collection<File> values = GitUtils.getGitDirs(path, Traverse.GO_UP).values();
		return values.isEmpty() ? null : values.toArray(new File[] {})[0];
	}

	public static File getGitDir(File file) {
		if (file.exists()) {
			while (file != null) {
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					return file;
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					return new File(file, Constants.DOT_GIT);
				}
				file = file.getParentFile();
			}
		}
		return null;
	}

	public static Map<IPath, File> getGitDirs(IPath path, Traverse traverse) throws CoreException {
		IPath p = path.removeFirstSegments(1);
		IFileStore fileStore = NewFileServlet.getFileStore(p);
		if (fileStore == null)
			return null;
		File file = fileStore.toLocalFile(EFS.NONE, null);

		Map<IPath, File> result = new HashMap<IPath, File>();
		switch (traverse) {
			case CURRENT :
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					result.put(new Path(""), file); //$NON-NLS-1$
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					result.put(new Path(""), new File(file, Constants.DOT_GIT)); //$NON-NLS-1$
				}
				break;
			case GO_UP :
				getGitDirsInParents(file, result);
				break;
			case GO_DOWN :
				getGitDirsInChildren(path, result);
				break;
		}
		return result;
	}

	/**
	 * Returns the path representing the clone resource for the given path.
	 * If the path is not a Git repository or an error occurred while transforming
	 * the given path into a store <code>null</code> is returned.
	 * @param path expected format /file/{projectId}[/{path}]
	 * @return the path representing the clone resource if found or <code>null</code>
	 * if the given path cannot be resolved to a file or it's not under control
	 *  of a git repository
	 * @throws CoreException 
	 */
	public static IPath getGitRootPath(IPath path) throws CoreException {
		Map<IPath, File> paths = getGitDirs(path, Traverse.GO_UP);
		if (paths == null || paths.isEmpty())
			return null;
		IPath modifier = paths.keySet().iterator().next();
		return path.append(modifier);
	}

	private static void getGitDirsInParents(File file, Map<IPath, File> gitDirs) {
		int levelUp = 0;
		while (file != null) {
			if (file.exists()) {
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					gitDirs.put(getPathForLevelUp(levelUp), file);
					return;
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					gitDirs.put(getPathForLevelUp(levelUp), new File(file, Constants.DOT_GIT));
					return;
				}
			}
			file = file.getParentFile();
			levelUp++;
		}
		return;
	}

	private static IPath getPathForLevelUp(int levelUp) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < levelUp; i++) {
			sb.append("../"); //$NON-NLS-1$
		}
		return new Path(sb.toString());
	}

	private static void getGitDirsInChildren(IPath path, Map<IPath, File> gitDirs) throws CoreException {
		if (WebProject.exists(path.segment(0))) {
			WebProject webProject = WebProject.fromId(path.segment(0));
			IFileStore store = webProject.getProjectStore().getFileStore(path.removeFirstSegments(1));
			File file = store.toLocalFile(EFS.NONE, null);
			if (file.exists() && file.isDirectory()) {
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					gitDirs.put(path.addTrailingSeparator(), file);
					return;
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					gitDirs.put(path.addTrailingSeparator(), new File(file, Constants.DOT_GIT));
					return;
				}
				File[] folders = file.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return file.isDirectory() && !file.getName().equals(Constants.DOT_GIT);
					}
				});
				for (File folder : folders) {
					getGitDirsInChildren(path.append(folder.getName()), gitDirs);
				}
				return;
			}
		}
	}

	public static String getRelativePath(IPath filePath, IPath pathToGitRoot) {
		StringBuilder sb = new StringBuilder();
		String file = null;
		if (!filePath.hasTrailingSeparator()) {
			file = filePath.lastSegment();
			filePath = filePath.removeLastSegments(1);
		}
		for (int i = 0; i < pathToGitRoot.segments().length; i++) {
			if (pathToGitRoot.segments()[i].equals(".."))
				sb.append(filePath.segment(filePath.segments().length - pathToGitRoot.segments().length + i)).append("/");
			// else TODO
		}
		if (file != null)
			sb.append(file);
		return sb.toString();
	}

}
