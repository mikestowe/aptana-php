package com.aptana.editor.php.internal.contentAssist;

import static com.aptana.editor.php.internal.contentAssist.PHPContentAssistProcessor.DOLLAR_SIGN;
import static com.aptana.editor.php.internal.contentAssist.PHPContentAssistProcessor.EMPTY_STRING;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aptana.editor.php.indexer.IElementEntry;
import com.aptana.editor.php.indexer.IElementsIndex;
import com.aptana.editor.php.indexer.IPHPIndexConstants;
import com.aptana.editor.php.internal.core.builder.IModule;
import com.aptana.editor.php.internal.indexer.ClassPHPEntryValue;
import com.aptana.editor.php.internal.indexer.FunctionPHPEntryValue;
import com.aptana.editor.php.internal.indexer.VariablePHPEntryValue;
import com.aptana.editor.php.internal.parser.nodes.PHPClassParseNode;
import com.aptana.editor.php.internal.parser.nodes.PHPFunctionParseNode;
import com.aptana.editor.php.internal.parser.nodes.PHPVariableParseNode;
import com.aptana.editor.php.internal.parser.nodes.Parameter;
import com.aptana.parsing.ast.IParseNode;

/**
 * A class that contains utility functions that are used when collecting proposals in the PHP content assist processor.
 * 
 * @author Shalom Gibly <sgibly@aptana.com>
 */
public class ContentAssistCollectors
{

	/**
	 * Collects entries for the variable.
	 * 
	 * @param index
	 *            - index to use.
	 * @param varName
	 *            - variable name (including $ sign).
	 * @param types
	 *            - types to collect variables from.
	 * @param exactMatch
	 *            - whether to check for exact match of the variable name.
	 * @param aliases
	 * @param namespace
	 * @param filter
	 *            - filter to apply.
	 * @return types
	 */
	public static Set<IElementEntry> collectVariableEntries(IElementsIndex index, String varName, Set<String> types,
			boolean exactMatch, Map<String, String> aliases, final String namespace)
	{
		Set<IElementEntry> result = new LinkedHashSet<IElementEntry>();
		// searching for variables
		String rightVarName = varName.substring(1);
		for (String leftType : types)
		{
			String typeName = processTypeName(aliases, namespace, leftType);
			String entryPath = typeName + rightVarName;
			List<IElementEntry> currentEntries = null;
			if (exactMatch)
			{
				currentEntries = index.getEntries(IPHPIndexConstants.VAR_CATEGORY, entryPath);
			}
			else
			{
				currentEntries = index.getEntriesStartingWith(IPHPIndexConstants.VAR_CATEGORY, entryPath);
			}
			ArrayList<?> items = ContentAssistUtils.selectModelElements(leftType, true);
			if (items != null && !items.isEmpty())
			{
				String lowCaseFuncName = varName != null ? varName.toLowerCase() : EMPTY_STRING;
				for (Object obj : items)
				{
					if (obj instanceof PHPClassParseNode)
					{
						final PHPClassParseNode classParseNode = (PHPClassParseNode) obj;
						IParseNode[] children = classParseNode.getChildren();
						if (children != null)
						{
							for (IParseNode child : children)
							{
								if (child instanceof PHPVariableParseNode)
								{
									final PHPVariableParseNode varParseNode = (PHPVariableParseNode) child;
									if (!varParseNode.isField())
										continue;
									String funcNodeName = varParseNode.getNodeName();
									if (funcNodeName != null)
									{
										if (!funcNodeName.startsWith(DOLLAR_SIGN))
										{
											funcNodeName = DOLLAR_SIGN + funcNodeName;
										}
										String lowCaseFuncNodeName = funcNodeName.toLowerCase();
										if (exactMatch)
										{
											if (!lowCaseFuncName.equals(lowCaseFuncNodeName))
												continue;
										}
										else
										{
											if (!lowCaseFuncNodeName.startsWith(lowCaseFuncName))
												continue;
										}
									}
									// Add an element entry
									result.add(new IElementEntry()
									{

										private VariablePHPEntryValue value;

										public int getCategory()
										{
											return IPHPIndexConstants.VAR_CATEGORY;
										}

										public String getEntryPath()
										{
											// Returns the function name.
											// This is useful when we have a
											// built-in class function
											// completion.
											return classParseNode.getNodeName() + IElementsIndex.DELIMITER
													+ varParseNode.getNodeName();
										}

										public String getLowerCaseEntryPath()
										{
											String path = getEntryPath();
											return path != null ? path.toLowerCase() : EMPTY_STRING;
										}

										public IModule getModule()
										{
											return null;
										}

										public Object getValue()
										{
											if (value != null)
											{
												return value;
											}
											value = new VariablePHPEntryValue(varParseNode.getModifiers(), varParseNode
													.isParameter(), varParseNode.isLocalVariable(), varParseNode
													.isField(), varParseNode.getType(), varParseNode
													.getStartingOffset(), namespace);
											return value;
										}

									});
								}
							}
						}
					}
				}
			}
			if (currentEntries != null)
			{
				result.addAll(currentEntries);
			}
		}
		return result;
	}

	/**
	 * Collects entries for the variable.
	 * 
	 * @param index
	 *            - index to use.
	 * @param varName
	 *            - variable name (including $ sign).
	 * @param types
	 *            - types to collect variables from.
	 * @param exactMatch
	 *            - whether to check for exact match of the variable name.
	 * @param aliases
	 * @param filter
	 *            - filter to apply.
	 * @param namespace
	 *            - namespace lookup (can be null)
	 * @return types
	 */
	public static Set<IElementEntry> collectConstEntries(IElementsIndex index, String varName, Set<String> types,
			boolean exactMatch, Map<String, String> aliases, String namespace)
	{
		Set<IElementEntry> result = new LinkedHashSet<IElementEntry>();
		// searching for variables
		String rightVarName = varName;
		for (String leftType : types)
		{
			String typeName = processTypeName(aliases, namespace, leftType);
			String entryPath = typeName + rightVarName;
			List<IElementEntry> currentEntries = null;
			if (exactMatch)
			{
				currentEntries = index.getEntries(IPHPIndexConstants.CONST_CATEGORY, entryPath);
			}
			else
			{
				currentEntries = index.getEntriesStartingWith(IPHPIndexConstants.CONST_CATEGORY, entryPath);
			}

			if (currentEntries != null)
			{
				result.addAll(currentEntries);
			}
		}

		return result;
	}

	/**
	 * Prepare the type name with the namespace.
	 * 
	 * @param aliases
	 * @param namespace
	 *            The namespace (should be without the separaotr at the beginning)
	 * @param typeName
	 *            The type that we want to append the namespace with
	 * @return A processed type name with a namespace prefix, in case a valid namespace was passed.
	 */
	private static String processTypeName(Map<String, String> aliases, String namespace, String typeName)
	{
		String result = typeName;
		if (namespace != null)
		{
			if (typeName.startsWith(namespace + PHPContentAssistProcessor.GLOBAL_NAMESPACE))
			{
				String type = typeName.substring(namespace.length() + 1);
				if (aliases.containsValue(type))
				{
					result = type;
				}
			}
			else if (!aliases.containsValue(typeName) && !typeName.contains(PHPContentAssistProcessor.GLOBAL_NAMESPACE) && !PHPContentAssistProcessor.GLOBAL_NAMESPACE.equals(namespace))
			{
				result = namespace + PHPContentAssistProcessor.GLOBAL_NAMESPACE + typeName;
			}
		}
		return result + IElementsIndex.DELIMITER;
	}

	/**
	 * Collects function entries.
	 * 
	 * @param index
	 *            - index to use.
	 * @param funcName
	 *            - function name.
	 * @param types
	 *            - types to collect functions (methods) from.
	 * @param exactMatch
	 *            - whether to check for exact match of the function name.
	 * @param aliases
	 * @param namespace
	 * @return types
	 */
	public static Set<IElementEntry> collectFunctionEntries(IElementsIndex index, String funcName, Set<String> types,
			boolean exactMatch, Map<String, String> aliases, String namespace)
	{
		Set<IElementEntry> result = new LinkedHashSet<IElementEntry>();

		// searching for methods
		String rightMethodName = funcName;
		for (String leftType : types)
		{
			String typeName = processTypeName(aliases, namespace, leftType);
			String entryPath = typeName + rightMethodName;
			List<IElementEntry> currentEntries = null;
			if (exactMatch)
			{
				currentEntries = index.getEntries(IPHPIndexConstants.FUNCTION_CATEGORY, entryPath);
			}
			else
			{
				currentEntries = index.getEntriesStartingWith(IPHPIndexConstants.FUNCTION_CATEGORY, entryPath);
			}
			if (currentEntries == null || currentEntries.isEmpty())
			{
				// PHPGlobalIndexer.getInstance().getIndex().getEntries(-1,
				// leftType.toLowerCase())
				ArrayList<?> items = ContentAssistUtils.selectModelElements(leftType, true);
				if (items != null && !items.isEmpty())
				{
					String lowCaseFuncName = funcName != null ? funcName.toLowerCase() : EMPTY_STRING;
					for (Object obj : items)
					{
						if (obj instanceof PHPClassParseNode)
						{
							final PHPClassParseNode classParseNode = (PHPClassParseNode) obj;
							IParseNode[] children = classParseNode.getChildren();
							if (children != null)
							{
								for (IParseNode child : children)
								{
									if (child instanceof PHPFunctionParseNode)
									{
										final PHPFunctionParseNode functionParseNode = (PHPFunctionParseNode) child;
										String funcNodeName = functionParseNode.getNodeName();
										if (funcNodeName != null)
										{
											String lowCaseFuncNodeName = funcNodeName.toLowerCase();
											if (exactMatch)
											{
												if (!lowCaseFuncName.equals(lowCaseFuncNodeName))
													continue;
											}
											else
											{
												if (!lowCaseFuncNodeName.startsWith(lowCaseFuncName))
													continue;
											}
										}
										// Add an element entry for an element
										// that is probably located in the
										// phpfunctions5
										// or outside the workspace.
										result.add(new IElementEntry()
										{

											private FunctionPHPEntryValue value;

											public int getCategory()
											{
												return IPHPIndexConstants.FUNCTION_CATEGORY;
											}

											public String getEntryPath()
											{
												// Returns the function name.
												// This is useful when we have a
												// built-in class function
												// completion.
												return classParseNode.getNodeName() + IElementsIndex.DELIMITER
														+ functionParseNode.getNodeName();
											}

											public String getLowerCaseEntryPath()
											{
												String path = getEntryPath();
												return path != null ? path.toLowerCase() : EMPTY_STRING;
											}

											public IModule getModule()
											{
												return null;
											}

											public Object getValue()
											{
												if (value != null)
												{
													return value;
												}
												Parameter[] parameters = functionParseNode.getParameters();
												LinkedHashMap<String, Set<Object>> parametersMap = null;
												boolean[] mandatories = null;
												ArrayList<Integer> startPositions = null;
												parametersMap = new LinkedHashMap<String, Set<Object>>(
														parameters.length);
												if (parameters != null)
												{
													mandatories = new boolean[parameters.length];
													startPositions = new ArrayList<Integer>(parameters.length);
													for (int i = 0; i < parameters.length; i++)
													{
														Parameter parameter = parameters[i];
														String nameIdentifier = parameter.getVariableName();
														if (nameIdentifier == null)
														{

															continue;
														}
														if (nameIdentifier.startsWith(DOLLAR_SIGN))
														{
															nameIdentifier = nameIdentifier.substring(1);
														}
														String parameterType = parameter.getClassType();
														Set<Object> types = null;
														if (parameterType != null)
														{
															types = new HashSet<Object>(1);
															types.add(parameterType);
														}
														parametersMap.put(nameIdentifier, types);
														mandatories[i] = EMPTY_STRING.equals(parameter
																.getDefaultValue());
														// Always set to that, since we have no other information here
														startPositions.add(functionParseNode.getStartingOffset());
													}
												}
												int[] startPositionsArray = new int[startPositions.size()];
												for (int p = 0; p < startPositions.size(); p++)
												{
													startPositionsArray[p] = startPositions.get(p);
												}
												value = new FunctionPHPEntryValue(functionParseNode.getModifiers(),
														true, parametersMap, startPositionsArray, mandatories,
														functionParseNode.getStartingOffset(), EMPTY_STRING);
												return value;
											}

										});
									}
								}
							}
						}
					}
				}
			}
			if (currentEntries != null)
			{
				result.addAll(currentEntries);
			}
		}

		return result;
	}

	/**
	 * Collects function entries.
	 * 
	 * @param types
	 *            - types to collect.
	 * @param exactMatch
	 *            - whether to check for exact match of the function name.
	 * @return A set of collected {@link IElementEntry}s with the requested types.
	 */
	public static Set<IElementEntry> collectBuiltinTypeEntries(Set<String> types, boolean exactMatch)
	{
		Set<IElementEntry> result = new LinkedHashSet<IElementEntry>();
		for (String typeName : types)
		{
			ArrayList<?> items = ContentAssistUtils.selectModelElements(typeName, true);
			if (items != null && !items.isEmpty())
			{
				String lowCaseClassName = typeName != null ? typeName.toLowerCase() : EMPTY_STRING;
				for (Object obj : items)
				{
					if (obj instanceof PHPClassParseNode)
					{
						final PHPClassParseNode classParseNode = (PHPClassParseNode) obj;
						String nodeName = classParseNode.getNodeName();
						if (nodeName != null)
						{
							String lowCaseClassNodeName = nodeName.toLowerCase();
							if (exactMatch)
							{
								if (!lowCaseClassName.equals(lowCaseClassNodeName))
									continue;
							}
							else
							{
								if (!lowCaseClassNodeName.startsWith(lowCaseClassName))
									continue;
							}

							// Add the builtin element
							result.add(new IElementEntry()
							{
								private ClassPHPEntryValue value;

								public int getCategory()
								{
									return IPHPIndexConstants.CLASS_CATEGORY;
								}

								public String getEntryPath()
								{
									// Returns the class name.
									return classParseNode.getNodeName();
								}

								public String getLowerCaseEntryPath()
								{
									String path = getEntryPath();
									return path != null ? path.toLowerCase() : EMPTY_STRING;
								}

								public IModule getModule()
								{
									return null;
								}

								public Object getValue()
								{
									if (value != null)
									{
										return value;
									}
									String superClassname = classParseNode.getSuperClassname();
									List<String> interfaces = classParseNode.getInterfaces();
									value = new ClassPHPEntryValue(classParseNode.getModifiers(), superClassname,
											interfaces, EMPTY_STRING);
									return value;
								}

							});
						}
					}
				}
			}
		}
		return result;
	}
}
