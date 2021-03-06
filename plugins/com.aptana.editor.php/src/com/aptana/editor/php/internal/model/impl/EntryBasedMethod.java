/**
 * This file Copyright (c) 2005-2008 Aptana, Inc. This program is
 * dual-licensed under both the Aptana Public License and the GNU General
 * Public license. You may elect to use one or the other of these licenses.
 * 
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT. Redistribution, except as permitted by whichever of
 * the GPL or APL you select, is prohibited.
 *
 * 1. For the GPL license (GPL), you can redistribute and/or modify this
 * program under the terms of the GNU General Public License,
 * Version 3, as published by the Free Software Foundation.  You should
 * have received a copy of the GNU General Public License, Version 3 along
 * with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Aptana provides a special exception to allow redistribution of this file
 * with certain other free and open source software ("FOSS") code and certain additional terms
 * pursuant to Section 7 of the GPL. You may view the exception and these
 * terms on the web at http://www.aptana.com/legal/gpl/.
 * 
 * 2. For the Aptana Public License (APL), this program and the
 * accompanying materials are made available under the terms of the APL
 * v1.0 which accompanies this distribution, and is available at
 * http://www.aptana.com/legal/apl/.
 * 
 * You may view the GPL, Aptana's exception and additional terms, and the
 * APL in the file titled license.html at the root of the corresponding
 * plugin containing this source file.
 * 
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.php.internal.model.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aptana.editor.php.core.model.IMethod;
import com.aptana.editor.php.core.model.ISourceRange;
import com.aptana.editor.php.core.model.env.ModelElementInfo;
import com.aptana.editor.php.core.model.env.SourceMethodElementInfo;
import com.aptana.editor.php.indexer.IElementEntry;
import com.aptana.editor.php.internal.indexer.FunctionPHPEntryValue;

/**
 * EntryBasedMethod
 * 
 * @author Denis Denisenko
 */
public class EntryBasedMethod extends AbstractMember implements IMethod
{
	/**
	 * Value.
	 */
	private FunctionPHPEntryValue entryValue;

	/**
	 * EntryBasedMethod constructor.
	 * 
	 * @param methodEntry
	 *            - method entry.
	 */
	public EntryBasedMethod(IElementEntry methodEntry)
	{
		super(methodEntry);

		// if (!EntryUtils.isMethod(methodEntry))
		// {
		// throw new IllegalArgumentException("method entry required");
		// }

		this.entryValue = (FunctionPHPEntryValue) methodEntry.getValue();
	}

	/**
	 * {@inheritDoc}
	 */
	public List<String> getParameters()
	{
		Set<String> keys = entryValue.getParameters().keySet();
		if (keys == null || keys.size() == 0)
		{
			return Collections.emptyList();
		}

		List<String> result = new ArrayList<String>();
		result.addAll(keys);
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getFlags()
	{
		return entryValue.getModifiers();
	}

	/**
	 * {@inheritDoc}
	 */
	public ISourceRange getNameRange()
	{
		// TODO add name length
		return new SourceRange(entryValue.getStartOffset());
	}

	/**
	 * {@inheritDoc}
	 */
	public int getElementType()
	{
		return METHOD;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isConstructor()
	{
		// TODO Implement this
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<String> getDirectParameterTypes()
	{
		Map<String, Set<Object>> paramsMap = entryValue.getParameters();

		List<String> result = new ArrayList<String>(paramsMap.size());
		for (Set<Object> paramTypes : paramsMap.values())
		{
			if (paramTypes.size() == 1)
			{
				Object type = paramTypes.iterator().next();
				if (type != null && type instanceof String)
				{
					result.add((String) type);
				}
				else
				{
					result.add(null);
				}
			}
		}

		return result;
	}

	public int getModifiers()
	{
		return entryValue.getModifiers();
	}

	/**
	 * {@inheritDoc}
	 */
	public ModelElementInfo getElementInfo()
	{
		SourceMethodElementInfo info = new SourceMethodElementInfo();
		info.setFlags(getFlags());
		info.setNameSourceStart(getSourceRange().getOffset());

		List<String> parameters = getParameters();
		if (parameters != null)
		{
			info.setArgumentNames(parameters.toArray(new String[parameters.size()]));
		}

		List<String> directParameterTypes = getDirectParameterTypes();
		if (directParameterTypes != null)
		{
			info.setArgumentInializers(directParameterTypes.toArray(new String[directParameterTypes.size()]));
		}

		return info;
	}
}
