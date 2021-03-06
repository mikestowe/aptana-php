/**
 * Copyright (c) 2005-2006 Aptana, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html. If redistributing this code,
 * this entire header must remain intact.
 */
package com.aptana.editor.php.internal.parser.nodes;

import org.eclipse.osgi.util.NLS;

/**
 * @author Pavel Petrochenko
 */
public class Messages extends NLS
{
	private static final String BUNDLE_NAME = "com.aptana.editor.php.internal.parser.nodes.messages"; //$NON-NLS-1$
	/**
	 * PHPExtendsNode_NonOnBuildPath0
	 */
	public static String PHPExtendsNode_NonOnBuildPath0;
	static
	{
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages()
	{
	}
}
