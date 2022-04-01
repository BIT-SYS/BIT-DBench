/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Checkout a branch to the working tree
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-checkout.html"
 *      >Git documentation about Checkout</a>
 */
public class CheckoutCommand extends GitCommand<Ref> {
	private String name;

	private boolean force = false;

	private boolean createBranch = false;

	private CreateBranchCommand.SetupUpstreamMode upstreamMode;

	private String startPoint = Constants.HEAD;

	private RevCommit startCommit;

	private CheckoutResult status;

	/**
	 * @param repo
	 */
	protected CheckoutCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @throws RefAlreadyExistsException
	 *             when trying to create (without force) a branch with a name
	 *             that already exists
	 * @throws RefNotFoundException
	 *             if the start point or branch can not be found
	 * @throws InvalidRefNameException
	 *             if the provided name is <code>null</code> or otherwise
	 *             invalid
	 * @return the newly created branch
	 */
	public Ref call() throws JGitInternalException, RefAlreadyExistsException,
			RefNotFoundException, InvalidRefNameException {
		checkCallable();
		processOptions();
		try {

			if (createBranch) {
				Git git = new Git(repo);
				CreateBranchCommand command = git.branchCreate();
				command.setName(name);
				command.setStartPoint(getStartPoint().name());
				if (upstreamMode != null)
					command.setUpstreamMode(upstreamMode);
				command.call();
			}

			RevWalk revWalk = new RevWalk(repo);
			Ref headRef = repo.getRef(Constants.HEAD);
			RevCommit headCommit = revWalk.parseCommit(headRef.getObjectId());
			String refLogMessage = "checkout: moving from "
					+ headRef.getTarget().getName();
			ObjectId branch = repo.resolve(name);

			if (branch == null)
				throw new RefNotFoundException(MessageFormat.format(JGitText
						.get().refNotResolved, name));

			RevCommit newCommit = revWalk.parseCommit(branch);

			DirCacheCheckout dco = new DirCacheCheckout(repo, headCommit
					.getTree(), repo.lockDirCache(), newCommit.getTree());
			dco.setFailOnConflict(true);
			try {
				dco.checkout();
			} catch (CheckoutConflictException e) {
				status = new CheckoutResult(Status.CONFLICTS, dco
						.getConflicts());
				throw e;
			}
			Ref ref = repo.getRef(name);
			if (ref != null && !ref.getName().startsWith(Constants.R_HEADS))
				ref = null;
			RefUpdate refUpdate = repo.updateRef(Constants.HEAD, ref == null);
			refUpdate.setForceUpdate(force);
			refUpdate.setRefLogMessage(refLogMessage + " to "
					+ newCommit.getName(), false);
			Result updateResult;
			if (ref != null)
				updateResult = refUpdate.link(ref.getName());
			else {
				refUpdate.setNewObjectId(newCommit);
				updateResult = refUpdate.forceUpdate();
			}

			setCallable(false);

			boolean ok = false;
			switch (updateResult) {
			case NEW:
				ok = true;
				break;
			case NO_CHANGE:
			case FAST_FORWARD:
			case FORCED:
				ok = true;
				break;
			default:
				break;
			}

			if (!ok)
				throw new JGitInternalException(MessageFormat.format(JGitText
						.get().checkoutUnexpectedResult, updateResult.name()));

			if (!dco.getToBeDeleted().isEmpty()) {
				status = new CheckoutResult(Status.NONDELETED, dco
						.getToBeDeleted());
			}
			else
				status = CheckoutResult.OK_RESULT;
			return ref;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		} finally {
			if (status == null)
				status = CheckoutResult.ERROR_RESULT;
		}
	}

	private ObjectId getStartPoint() throws AmbiguousObjectException,
			RefNotFoundException, IOException {
		if (startCommit != null)
			return startCommit.getId();
		ObjectId result = null;
		try {
			result = repo.resolve((startPoint == null) ? Constants.HEAD
					: startPoint);
		} catch (AmbiguousObjectException e) {
			throw e;
		}
		if (result == null)
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().refNotResolved,
					startPoint != null ? startPoint : Constants.HEAD));
		return result;
	}

	private void processOptions() throws InvalidRefNameException {
		if (name == null
				|| !Repository.isValidRefName(Constants.R_HEADS + name))
			throw new InvalidRefNameException(MessageFormat.format(JGitText
					.get().branchNameInvalid, name == null ? "<null>" : name));
	}

	/**
	 * @param name
	 *            the name of the new branch
	 * @return this instance
	 */
	public CheckoutCommand setName(String name) {
		checkCallable();
		this.name = name;
		return this;
	}

	/**
	 * @param createBranch
	 *            if <code>true</code> a branch will be created as part of the
	 *            checkout and set to the specified start point
	 * @return this instance
	 */
	public CheckoutCommand setCreateBranch(boolean createBranch) {
		checkCallable();
		this.createBranch = createBranch;
		return this;
	}

	/**
	 * @param force
	 *            if <code>true</code> and the branch with the given name
	 *            already exists, the start-point of an existing branch will be
	 *            set to a new start-point; if false, the existing branch will
	 *            not be changed
	 * @return this instance
	 */
	public CheckoutCommand setForce(boolean force) {
		checkCallable();
		this.force = force;
		return this;
	}

	/**
	 * @param startPoint
	 *            corresponds to the start-point option; if <code>null</code>,
	 *            the current HEAD will be used
	 * @return this instance
	 */
	public CheckoutCommand setStartPoint(String startPoint) {
		checkCallable();
		this.startPoint = startPoint;
		this.startCommit = null;
		return this;
	}

	/**
	 * @param startCommit
	 *            corresponds to the start-point option; if <code>null</code>,
	 *            the current HEAD will be used
	 * @return this instance
	 */
	public CheckoutCommand setStartPoint(RevCommit startCommit) {
		checkCallable();
		this.startCommit = startCommit;
		this.startPoint = null;
		return this;
	}

	/**
	 * @param mode
	 *            corresponds to the --track/--no-track options; may be
	 *            <code>null</code>
	 * @return this instance
	 */
	public CheckoutCommand setUpstreamMode(
			CreateBranchCommand.SetupUpstreamMode mode) {
		checkCallable();
		this.upstreamMode = mode;
		return this;
	}

	/**
	 * @return the result
	 */
	public CheckoutResult getResult() {
		if (status == null)
			return CheckoutResult.NOT_TRIED_RESULT;
		return status;
	}
}
