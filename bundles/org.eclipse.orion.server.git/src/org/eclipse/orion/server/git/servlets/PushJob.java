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
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a push operation in the background
 */
public class PushJob extends GitJob {

	private final CredentialsProvider credentials;
	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private Path p;
	private String srcRef;
	private boolean tags;

	public PushJob(CredentialsProvider credentials, Path path, String srcRef, boolean tags) {
		super("Pushing"); //$NON-NLS-1$

		this.credentials = credentials;
		this.p = path;
		this.srcRef = srcRef;
		this.tags = tags;
		this.task = createTask();
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Pushing {0}...", p.segment(0)));
		getTaskService().updateTask(info);
		return info;
	}

	private IStatus doPush() throws IOException, CoreException, JGitInternalException, InvalidRemoteException, URISyntaxException, JSONException {
		// /git/remote/{remote}/{branch}/file/{path}
		File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
		Repository db = new FileRepository(gitDir);
		Git git = new Git(db);

		PushCommand pushCommand = git.push();

		RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), p.segment(0));
		((GitCredentialsProvider) credentials).setUri(remoteConfig.getURIs().get(0));
		pushCommand.setCredentialsProvider(credentials);

		// ObjectId ref = db.resolve(srcRef);
		RefSpec spec = new RefSpec(srcRef + ":" + Constants.R_HEADS + p.segment(1)); //$NON-NLS-1$
		pushCommand.setRemote(p.segment(0)).setRefSpecs(spec);
		if (tags)
			pushCommand.setPushTags();
		Iterable<PushResult> resultIterable = pushCommand.call();
		PushResult pushResult = resultIterable.iterator().next();
		// this set will contain only OK status or UP_TO_DATE status
		Set<RemoteRefUpdate.Status> statusSet = new HashSet<RemoteRefUpdate.Status>();
		for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
			final String rm = rru.getRemoteName();
			// final String sr = rru.isDelete() ? null : rru.getSrcRef();
			// check status only for branch given in the URL or tags
			if (p.segment(1).equals(Repository.shortenRefName(rm)) || rm.startsWith(Constants.R_TAGS)) {
				RemoteRefUpdate.Status status = rru.getStatus();
				// any status different from UP_TO_DATE and OK should generate warning
				if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE)
					return new Status(IStatus.WARNING, GitActivator.PI_GIT, status.name());
				// add OK or UP_TO_DATE status to the set
				statusSet.add(status);
			}
			// TODO: return results for all updated branches once push is available for remote, see bug 342727, comment 1
		}
		if (statusSet.contains(RemoteRefUpdate.Status.OK))
			// if there is OK status in the set -> something was updated
			return Status.OK_STATUS;
		else
			// if there is no OK status in the set -> only UP_TO_DATE status is possible
			return new Status(IStatus.WARNING, GitActivator.PI_GIT, RemoteRefUpdate.Status.UP_TO_DATE.name());
	}

	public TaskInfo getTask() {
		return task;
	}

	private ITaskService getTaskService() {
		BundleContext context = GitActivator.getDefault().getBundleContext();
		taskServiceRef = context.getServiceReference(ITaskService.class);
		if (taskServiceRef == null)
			throw new IllegalStateException("Task service not available"); //$NON-NLS-1$
		taskService = context.getService(taskServiceRef);
		if (taskService == null)
			throw new IllegalStateException("Task service not available"); //$NON-NLS-1$
		return taskService;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPush();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "Error pushing git remote");
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git repository", e); //$NON-NLS-1$
		}
		task.done(result);
		task.setMessage(NLS.bind("Pushing {0} done", p.segment(0)));
		updateTask();
		taskService = null;
		GitActivator.getDefault().getBundleContext().ungetService(taskServiceRef);
		return Status.OK_STATUS;
	}

	private void updateTask() {
		getTaskService().updateTask(task);
	}
}
