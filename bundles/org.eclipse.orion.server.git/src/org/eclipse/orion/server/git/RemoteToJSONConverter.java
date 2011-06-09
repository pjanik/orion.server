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
package org.eclipse.orion.server.git;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.lib.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.*;

/**
 * Generates JSON representation of the given remote.
 */
public class RemoteToJSONConverter {

	public static JSONObject toJSON(String remoteName, Repository db, URI baseLocation, BaseToRemoteConverter baseToRemoteConverter) throws JSONException, URISyntaxException, IOException, CoreException {

		JSONObject result = new JSONObject();
		Set<String> configNames = db.getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);

		for (String configName : configNames) {
			if (configName.equals(remoteName)) {
				result.put(ProtocolConstants.KEY_NAME, configName);
				result.put(ProtocolConstants.KEY_TYPE, GitConstants.KEY_REMOTE_NAME);
				result.put(ProtocolConstants.KEY_LOCATION, baseToRemoteConverter.baseToRemoteLocation(baseLocation, remoteName, "" /* no branch name */)); //$NON-NLS-1$

				JSONArray children = new JSONArray();
				List<Ref> refs = new ArrayList<Ref>();
				for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES + remoteName + "/").entrySet()) {
					if (!refEntry.getValue().isSymbolic()) {
						Ref ref = refEntry.getValue();
						String name = ref.getName();
						name = Repository.shortenRefName(name).substring(Constants.DEFAULT_REMOTE_NAME.length() + 1);
						if (db.getBranch().equals(name)) {
							refs.add(0, ref);
						} else {
							refs.add(ref);
						}
					}
				}
				for (Ref ref : refs) {
					JSONObject o = new JSONObject();
					String name = ref.getName();
					o.put(ProtocolConstants.KEY_NAME, name);
					o.put(ProtocolConstants.KEY_TYPE, GitConstants.REMOTE_TRACKING_BRANCH_TYPE);
					o.put(ProtocolConstants.KEY_ID, ref.getObjectId().name());
					// see bug 342602
					// o.put(GitConstants.KEY_COMMIT, baseToCommitLocation(baseLocation, name));
					o.put(ProtocolConstants.KEY_LOCATION, BaseToRemoteConverter.REMOVE_FIRST_3.baseToRemoteLocation(baseLocation, "" /*short name is {remote}/{branch}*/, Repository.shortenRefName(name))); //$NON-NLS-1$
					o.put(GitConstants.KEY_COMMIT, BaseToCommitConverter.getCommitLocation(baseLocation, ref.getObjectId().name(), BaseToCommitConverter.REMOVE_FIRST_3));
					o.put(GitConstants.KEY_HEAD, BaseToCommitConverter.getCommitLocation(baseLocation, Constants.HEAD, BaseToCommitConverter.REMOVE_FIRST_3));
					o.put(GitConstants.KEY_CLONE, BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.REMOTE_BRANCH));
					o.put(GitConstants.KEY_BRANCH, BaseToBranchConverter.getBranchLocation(baseLocation, BaseToBranchConverter.REMOTE_BRANCH));
					o.put(GitConstants.KEY_INDEX, BaseToIndexConverter.getIndexLocation(baseLocation, BaseToIndexConverter.REMOTE_BRANCH));
					children.put(o);
				}
				result.put(ProtocolConstants.KEY_CHILDREN, children);
				return result;
			}
		}
		return null;
	}
}
