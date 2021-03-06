package com.aptana.editor.php.internal.contentAssist;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationExtension;
import org.eclipse.php.core.compiler.PHPFlags;
import org.eclipse.php.internal.core.ast.nodes.ClassDeclaration;
import org.eclipse.php.internal.core.documentModel.parser.regions.PHPRegionTypes;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import com.aptana.editor.common.contentassist.LexemeProvider;
import com.aptana.editor.php.indexer.IElementEntry;
import com.aptana.editor.php.indexer.IPHPIndexConstants;
import com.aptana.editor.php.internal.indexer.ClassPHPEntryValue;
import com.aptana.editor.php.internal.indexer.FunctionPHPEntryValue;
import com.aptana.editor.php.internal.indexer.NamespacePHPEntryValue;
import com.aptana.editor.php.internal.parser.nodes.IPHPParseNode;
import com.aptana.editor.php.internal.parser.nodes.PHPClassParseNode;
import com.aptana.editor.php.internal.parser.nodes.PHPFunctionParseNode;
import com.aptana.editor.php.internal.parser.nodes.PHPNamespaceNode;
import com.aptana.editor.php.internal.parser.nodes.Parameter;
import com.aptana.editor.php.internal.text.link.contentassist.LineBreakingReader;
import com.aptana.parsing.ast.IParseNode;
import com.aptana.parsing.lexer.Lexeme;

public class PHPContextCalculator
{
	/**
	 * "Extends" proposal context type.
	 */
	protected static final String EXTENDS_PROPOSAL_CONTEXT_TYPE = "EXTENDS_PROPOSAL_CONTEXT_TYPE"; //$NON-NLS-1$

	/**
	 * "Implements" proposal context type.
	 */
	protected static final String IMPLEMENTS_PROPOSAL_CONTEXT_TYPE = "IMPLEMENTS_PROPOSAL_CONTEXT_TYPE"; //$NON-NLS-1$

	/**
	 * "new" proposal context type.
	 */
	protected static final String NEW_PROPOSAL_CONTEXT_TYPE = "NEW_PROPOSAL_CONTEXT_TYPE"; //$NON-NLS-1$

	/**
	 * Namespace proposal context type.
	 */
	protected static final String NAMESPACE_PROPOSAL_CONTEXT_TYPE = "NAMESPACE_PROPOSAL_CONTEXT_TYPE"; //$NON-NLS-1$

	private static final String NEW_LINE = System.getProperty("line.separator", "\r\n"); //$NON-NLS-1$ //$NON-NLS-2$

	/**
	 * DEFAULT_DELIMITER
	 */
	public static final String DEFAULT_DELIMITER = NEW_LINE + "\u2022\t"; //$NON-NLS-1$

	// private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private ProposalContext currentContext;

	/**
	 * Calculate and return the {@link ProposalContext} for the given offset.
	 * 
	 * @param lexemeProvider
	 * @param offset
	 * @return
	 */
	public ProposalContext calculateCompletionContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset)
	{
		internalCalculateContext(lexemeProvider, offset);
		return currentContext;
	}

	/*
	 * Do the actual calculation
	 */
	private void internalCalculateContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset)
	{
		currentContext = new ProposalContext(new AcceptAllContextFilter(), true, true, null);
		int lexemePosition = lexemeProvider.getLexemeFloorIndex(offset - 1);

		// checking line-comment context
		if (checkLineCommentContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// checking PHPDoc context
		if (checkPHPDocContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// checking class declaration context
		if (checkClassDeclarationContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// check implements declaration context
		if (checkImplementsDeclarationContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// check extends declaration context
		if (checkExtendsDeclarationContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// checking class "extends" or "implements" declaration context
		if (checkClassExtendsOrImplementsContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// checking function declaration context
		if (checkFunctionDeclarationContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// checking new instance context
		if (checkNewInstanceContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}

		// checking for namespace Use statement
		if (checkNamespaceUseContext(lexemeProvider, offset, lexemePosition))
		{
			return;
		}
	}

	/**
	 * Checks class declaration context and sets it if needed.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param offset
	 *            - offset.
	 * @param lexemePosition
	 *            - lexeme position (of offset).
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkClassDeclarationContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset,
			int lexemePosition)
	{
		if (lexemePosition == 0)
		{
			return false;
		}

		Lexeme<PHPTokenType> nearestKeyWord = findLexemeBackward(lexemeProvider, lexemePosition - 1,
				PHPRegionTypes.PHP_CLASS, new String[] { PHPRegionTypes.PHP_NS_SEPARATOR });
		if (nearestKeyWord == null)
		{
			nearestKeyWord = findLexemeBackward(lexemeProvider, lexemePosition - 1, PHPRegionTypes.PHP_INTERFACE,
					new String[] { PHPRegionTypes.PHP_NS_SEPARATOR });
			if (nearestKeyWord == null)
			{
				return false;
			}
		}

		currentContext = getDenyAllProposalContext();
		return true;
	}

	/**
	 * Checks class "extends" or "implements" context and sets it if needed. In case this method is returning true, only
	 * "extends" or "implements" keyword are displayed.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param offset
	 *            - offset.
	 * @param lexemePosition
	 *            - lexeme position (of offset).
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkClassExtendsOrImplementsContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset,
			int lexemePosition)
	{
		if (lexemePosition == 0)
		{
			return false;
		}

		Lexeme<PHPTokenType> nearestClassKeyWord = findLexemeBackward(lexemeProvider, lexemePosition - 1,
				PHPRegionTypes.PHP_CLASS, new String[] { PHPRegionTypes.PHP_STRING, PHPRegionTypes.WHITESPACE,
						PHPRegionTypes.PHP_TOKEN, PHPRegionTypes.PHP_EXTENDS });
		// WAS SKIPPING: , new int[] {PHPTokenTypes.IDENTIFIER});

		if (nearestClassKeyWord == null)
		{
			nearestClassKeyWord = findLexemeBackward(lexemeProvider, lexemePosition - 1, PHPRegionTypes.PHP_INTERFACE,
					new String[] { PHPRegionTypes.PHP_STRING, PHPRegionTypes.WHITESPACE, PHPRegionTypes.PHP_TOKEN });
			// WAS SKIPPING: , new int[] {PHPTokenTypes.IDENTIFIER});
			if (nearestClassKeyWord == null)
			{
				return false;
			}
		}

		// whether class or interface is being declared
		final boolean declaredClass = PHPRegionTypes.PHP_CLASS.equals(nearestClassKeyWord.getType().getType());
		// Check if this declarations already have an "extends" or an "implements" keyword
		Lexeme<PHPTokenType> extendsToken = findLexemeBackward(lexemeProvider, lexemePosition - 1,
				PHPRegionTypes.PHP_EXTENDS, new String[] { PHPRegionTypes.PHP_STRING, PHPRegionTypes.WHITESPACE,
						PHPRegionTypes.PHP_TOKEN, PHPRegionTypes.PHP_IMPLEMENTS });
		Lexeme<PHPTokenType> implementsToken = findLexemeBackward(lexemeProvider, lexemePosition - 1,
				PHPRegionTypes.PHP_IMPLEMENTS, new String[] { PHPRegionTypes.PHP_STRING, PHPRegionTypes.WHITESPACE,
						PHPRegionTypes.PHP_TOKEN });

		final boolean alreadyHaveExtends = (extendsToken != null);
		final boolean alreadyHaveImplements = (implementsToken != null);

		IContextFilter filter = new IContextFilter()
		{

			public boolean acceptBuiltin(Object builtinElement)
			{
				if (builtinElement instanceof IPHPParseNode)
				{
					if (!alreadyHaveExtends
							&& "extends".equals(((IPHPParseNode) builtinElement).getNodeName()) //$NON-NLS-1$
							|| (!alreadyHaveImplements && declaredClass && "implements".equals(((IPHPParseNode) builtinElement).getNodeName()))) //$NON-NLS-1$
					{
						return true;
					}
				}

				return false;
			}

			public boolean acceptElementEntry(IElementEntry element)
			{
				return false;
			}
		};

		currentContext = new ProposalContext(filter, true, false, new int[0]);
		currentContext.setAutoActivateCAAfterApply(true);
		return true;
	}

	/**
	 * Checks implements declaration context and sets it if needed.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param offset
	 *            - offset.
	 * @param lexemePosition
	 *            - lexeme position (of offset).
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkImplementsDeclarationContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset,
			int lexemePosition)
	{
		if (lexemePosition == 0)
		{
			return false;
		}

		if (checkImplementsDeclarationContextInternal(lexemeProvider, lexemePosition))
		{
			return true;
		}
		else
		{
			return checkImplementsDeclarationContextInternal(lexemeProvider, lexemePosition - 1);
		}
	}

	private boolean checkImplementsDeclarationContextInternal(LexemeProvider<PHPTokenType> lexemeProvider,
			int lexemePosition)
	{
		// searching for "implements" keyword
		Lexeme<PHPTokenType> nearestKeyWord = findLexemeBackward(lexemeProvider, lexemePosition,
				PHPRegionTypes.PHP_IMPLEMENTS, new String[] { PHPRegionTypes.PHP_STRING, PHPRegionTypes.WHITESPACE,
						PHPRegionTypes.PHP_TOKEN });
		// WAS SKIPPING: , new int[] {PHPTokenTypes.IDENTIFIER, PHPTokenTypes.COMMA });
		if (nearestKeyWord == null)
		{
			return false;
		}

		// the implements keyword found and now we have to check whether a
		// class is being declared
		Lexeme<PHPTokenType> classKeyword = findLexemeBackward(lexemeProvider, lexemePosition,
				PHPRegionTypes.PHP_CLASS, new String[] { PHPRegionTypes.PHP_EXTENDS, PHPRegionTypes.PHP_IMPLEMENTS,
						PHPRegionTypes.PHP_STRING, PHPRegionTypes.WHITESPACE, PHPRegionTypes.PHP_TOKEN });
		// WAS ALSO SKIPPING (in both cases above): PHPTokenTypes.IDENTIFIER, PHPTokenTypes.COMMA

		// wrong declaration, so no proposals at all
		if (classKeyword == null)
		{
			currentContext = getDenyAllProposalContext();
			return true;
		}

		IContextFilter filter = new IContextFilter()
		{

			public boolean acceptBuiltin(Object builtinElement)
			{
				if (builtinElement instanceof PHPClassParseNode)
				{
					PHPClassParseNode node = (PHPClassParseNode) builtinElement;
					if (PHPFlags.isInterface(node.getModifiers()))
					{
						return true;
					}
				}

				return false;
			}

			public boolean acceptElementEntry(IElementEntry element)
			{
				if (element.getValue() instanceof ClassPHPEntryValue)
				{
					if (PHPFlags.isInterface(((ClassPHPEntryValue) element.getValue()).getModifiers()))
					{
						return true;
					}
				}

				return false;
			}
		};

		currentContext = new ProposalContext(filter, true, true, new int[0]);
		currentContext.setType(IMPLEMENTS_PROPOSAL_CONTEXT_TYPE);
		return true;
	}

	/**
	 * Checks extends declaration context and sets it if needed.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param offset
	 *            - offset.
	 * @param lexemePosition
	 *            - lexeme position (of offset).
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkExtendsDeclarationContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset,
			int lexemePosition)
	{
		if (lexemePosition == 0)
		{
			return false;
		}

		if (checkExtendsDeclarationContextInternal(lexemeProvider, lexemePosition))
		{
			return true;
		}
		else
		{
			return checkExtendsDeclarationContextInternal(lexemeProvider, lexemePosition - 1);
		}
	}

	private boolean checkExtendsDeclarationContextInternal(LexemeProvider<PHPTokenType> lexemeProvider,
			int lexemePosition)
	{
		Lexeme<PHPTokenType> nearestKeyWord = findLexemeBackward(lexemeProvider, lexemePosition,
				PHPRegionTypes.PHP_EXTENDS, new String[] { PHPRegionTypes.PHP_STRING, PHPRegionTypes.WHITESPACE,
						PHPRegionTypes.PHP_TOKEN });
		// WAS SKIPPING: new int[] {PHPTokenTypes.COMMA, PHPTokenTypes.IDENTIFIER });
		if (nearestKeyWord == null)
		{
			return false;
		}

		// extends keyword found and now we have to check whether interface or
		// class is being declared.
		// Also, we need to make sure we do not allow multiple inheritance when a class extension is involved.
		Lexeme<PHPTokenType> classKeyword = findLexemeBackward(lexemeProvider, lexemePosition,
				PHPRegionTypes.PHP_CLASS, new String[] { PHPRegionTypes.PHP_EXTENDS, PHPRegionTypes.PHP_STRING,
						PHPRegionTypes.WHITESPACE, PHPRegionTypes.PHP_TOKEN });
		Lexeme<PHPTokenType> interfaceKeyword = findLexemeBackward(lexemeProvider, lexemePosition,
				PHPRegionTypes.PHP_INTERFACE, new String[] { PHPRegionTypes.PHP_EXTENDS, PHPRegionTypes.PHP_STRING,
						PHPRegionTypes.WHITESPACE, PHPRegionTypes.PHP_TOKEN });
		// WAS ALSO SKIPPING (in both cases): PHPTokenTypes.IDENTIFIER, PHPTokenTypes.COMMA

		// wrong declaration, so no proposals at all
		if (classKeyword == null && interfaceKeyword == null)
		{
			currentContext = getDenyAllProposalContext();
			return true;
		}

		final boolean classDeclared = classKeyword != null;

		// Check that we don't have any strings or commas that indicate a syntax error or a multiple inheritance
		if (classDeclared)
		{
			Lexeme<PHPTokenType> extendsOnly = findLexemeBackward(lexemeProvider, lexemePosition,
					PHPRegionTypes.PHP_EXTENDS, new String[] { PHPRegionTypes.WHITESPACE });
			if (extendsOnly == null)
			{
				// We have something else between the completion location and the extends keyword, so we block it.
				return false;
			}
		}

		IContextFilter filter = new IContextFilter()
		{

			public boolean acceptBuiltin(Object builtinElement)
			{
				if (builtinElement instanceof PHPClassParseNode)
				{
					PHPClassParseNode node = (PHPClassParseNode) builtinElement;
					if (ClassDeclaration.MODIFIER_FINAL == node.getModifiers())
					{
						return false;
					}
					boolean isInterface = PHPFlags.isInterface(node.getModifiers());
					if (classDeclared && !isInterface || !classDeclared && isInterface)
					{
						return true;
					}
				}

				return false;
			}

			public boolean acceptElementEntry(IElementEntry element)
			{
				if (element.getValue() instanceof ClassPHPEntryValue)
				{
					ClassPHPEntryValue value = (ClassPHPEntryValue) element.getValue();
					if (ClassDeclaration.MODIFIER_FINAL == value.getModifiers())
					{
						return false;
					}
					boolean isInterface = PHPFlags.isInterface(value.getModifiers());
					if (classDeclared && !isInterface || !classDeclared && isInterface)
					{
						return true;
					}
				}

				return false;
			}
		};

		currentContext = new ProposalContext(filter, true, true, new int[0]);
		currentContext.setType(EXTENDS_PROPOSAL_CONTEXT_TYPE);
		return true;
	}

	/**
	 * Checks function declaration context and sets it if needed.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param offset
	 *            - offset.
	 * @param lexemePosition
	 *            - lexeme position (of offset).
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkFunctionDeclarationContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset,
			int lexemePosition)
	{
		if (lexemePosition == 0)
		{
			return false;
		}

		Lexeme<PHPTokenType> nearestKeyWord = findLexemeBackward(lexemeProvider, lexemePosition - 1,
				PHPRegionTypes.PHP_FUNCTION, new String[] { PHPRegionTypes.PHP_STRING, PHPRegionTypes.PHP_TOKEN,
						PHPRegionTypes.WHITESPACE, PHPRegionTypes.PHP_VARIABLE });
		if (nearestKeyWord == null)
		{
			return false;
		}

		currentContext = getDenyAllProposalContext();
		return true;
	}

	/**
	 * Checks new instance context.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param offset
	 *            - offset.
	 * @param lexemePosition
	 *            - lexeme position.
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkNewInstanceContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset, int lexemePosition)
	{
		if (lexemePosition == 0)
		{
			return false;
		}

		if (checkNewInstanceContextInternal(lexemeProvider, lexemePosition))
		{
			return true;
		}
		else
		{
			return checkNewInstanceContextInternal(lexemeProvider, lexemePosition - 1);
		}
	}

	/**
	 * Checks new instance context.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param lexemePosition
	 *            - lexeme position.
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkNewInstanceContextInternal(LexemeProvider<PHPTokenType> lexemeProvider, int lexemePosition)
	{
		// searching for "new" keyword
		Lexeme<PHPTokenType> nearestKeyWord = findLexemeBackward(lexemeProvider, lexemePosition,
				PHPRegionTypes.PHP_NEW, new String[] { PHPRegionTypes.WHITESPACE, PHPRegionTypes.PHP_NS_SEPARATOR,
						PHPRegionTypes.PHP_STRING });
		// WAS SKIPPING: { PHPTokenTypes.IDENTIFIER, PHPTokenTypes.BACKSLASH });
		if (nearestKeyWord == null)
		{
			return false;
		}

		IContextFilter filter = new IContextFilter()
		{

			public boolean acceptBuiltin(Object builtinElement)
			{
				if (builtinElement instanceof PHPClassParseNode)
				{
					PHPClassParseNode node = (PHPClassParseNode) builtinElement;
					if (!PHPFlags.isInterface(node.getModifiers()))
					{
						return true;
					}
				}

				return false;
			}

			public boolean acceptElementEntry(IElementEntry element)
			{
				if (element.getValue() instanceof ClassPHPEntryValue)
				{
					if (!PHPFlags.isInterface(((ClassPHPEntryValue) element.getValue()).getModifiers()))
					{
						return true;
					}
				}

				return element.getValue() instanceof NamespacePHPEntryValue;
			}
		};

		currentContext = new ProposalContext(filter, true, true, new int[0]);
		currentContext.setType(NEW_PROPOSAL_CONTEXT_TYPE);
		return true;
	}

	/**
	 * Checks for line-comment context.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param lexemePosition
	 *            - lexeme position.
	 * @return true if context is recognized and set, false otherwise
	 */

	private boolean checkLineCommentContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset, int lexemePosition)
	{
		Lexeme<PHPTokenType> firstLexeme = lexemeProvider.getFirstLexeme();
		if (firstLexeme != null)
		{
			String lexemeType = firstLexeme.getType().getType();
			if (lexemeType.equals(PHPRegionTypes.PHP_COMMENT_START)
					|| lexemeType.equals(PHPRegionTypes.PHP_LINE_COMMENT))
			{
				currentContext = getDenyAllProposalContext();
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks for PHPDoc context.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param lexemePosition
	 *            - lexeme position.
	 * @return true if context is recognized and set, false otherwise
	 */

	private boolean checkPHPDocContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset, int lexemePosition)
	{
		Lexeme<PHPTokenType> firstLexeme = lexemeProvider.getFirstLexeme();
		if (firstLexeme != null && firstLexeme.getType().getType().equals(PHPRegionTypes.PHPDOC_COMMENT_START))
		{
			currentContext = getDenyAllProposalContext();
			return true;
		}
		return false;
	}

	/**
	 * Checks for namespace 'use' context.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param lexemePosition
	 *            - lexeme position.
	 * @return true if context is recognized and set, false otherwise
	 */
	private boolean checkNamespaceUseContext(LexemeProvider<PHPTokenType> lexemeProvider, int offset, int lexemePosition)
	{
		Lexeme<PHPTokenType> nearestUseKeyWord = findLexemeBackward(lexemeProvider, lexemePosition,
				PHPRegionTypes.PHP_USE, new String[] { PHPRegionTypes.WHITESPACE, PHPRegionTypes.PHP_NS_SEPARATOR,
						PHPRegionTypes.PHP_STRING });
		if (nearestUseKeyWord == null)
		{
			return false;
		}

		IContextFilter filter = new IContextFilter()
		{

			public boolean acceptBuiltin(Object builtinElement)
			{
				return (builtinElement instanceof PHPNamespaceNode);
			}

			public boolean acceptElementEntry(IElementEntry element)
			{
				Object value = element.getValue();
				return value instanceof NamespacePHPEntryValue || value instanceof ClassPHPEntryValue ;
			}
		};
		currentContext = new ProposalContext(filter, true, true, new int[] { IPHPIndexConstants.NAMESPACE_CATEGORY });
		currentContext.setType(NAMESPACE_PROPOSAL_CONTEXT_TYPE);
		return true;
	}

	/**
	 * Finds lexeme going backwards.
	 * 
	 * @param lexemeProvider
	 *            - lexeme provider.
	 * @param startPosition
	 *            - start position.
	 * @param typesToFind
	 *            - type of lexeme to find. Can be a String or an array of String types. If an array is passes, this
	 *            method will stop and return a Lexeme on the first match.
	 * @param allowedTypesToSkip
	 *            - types allowed to skip. empty array means no types can be skipped, null means any types can be
	 *            skipped.
	 * @return found lexeme or null if not found
	 */
	public static Lexeme<PHPTokenType> findLexemeBackward(LexemeProvider<PHPTokenType> lexemeProvider,
			int startPosition, Object typesToFind, String[] allowedTypesToSkip)
	{
		HashSet<String> typesSet = new HashSet<String>();
		if (typesToFind instanceof String)
		{
			typesSet.add(typesToFind.toString());
		}
		else
		{
			String[] types = (String[]) typesToFind;
			for (String type : types)
			{
				typesSet.add(type);
			}
		}
		for (int i = startPosition; i >= 0; i--)
		{
			Lexeme<PHPTokenType> currentLexeme = lexemeProvider.getLexeme(i);
			if (typesSet.contains(currentLexeme.getType().getType()))
			{
				return currentLexeme;
			}
			else
			{
				if (allowedTypesToSkip != null)
				{
					boolean allowedToSkip = false;
					for (int j = 0; j < allowedTypesToSkip.length; j++)
					{
						if (currentLexeme.getType().getType().equals(allowedTypesToSkip[j]))
						{
							allowedToSkip = true;
							break;
						}
					}
					if (!allowedToSkip)
					{
						return null;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Gets "Deny all" proposal context.
	 * 
	 * @return
	 */
	private static ProposalContext getDenyAllProposalContext()
	{
		IContextFilter filter = new IContextFilter()
		{

			public boolean acceptBuiltin(Object builtinElement)
			{
				return false;
			}

			public boolean acceptElementEntry(IElementEntry element)
			{
				return false;
			}
		};

		return new ProposalContext(filter, false, false, new int[0]);
	}

	/**
	 * Returns a CallInfo representing the function name or the class name that comes before the given offset.
	 * 
	 * @param lexemeProvider
	 * @param offset
	 * @return A {@link CallInfo}, or null if none is located.
	 */
	static CallInfo calculateCallInfo(LexemeProvider<PHPTokenType> lexemeProvider, int offset)
	{
		int startPosition = lexemeProvider.getLexemeFloorIndex(offset - 1);
		int level = 0;
		for (int i = startPosition; i >= 0; i--)
		{
			Lexeme<PHPTokenType> currentLexeme = lexemeProvider.getLexeme(i);
			String type = currentLexeme.getType().getType();
			if (PHPRegionTypes.PHP_NEW.equals(type) && lexemeProvider.size() - 2 > i)
			{
				// This is not a function call, but a class instantiation,
				// however, we return a CallInfo for that too.
				// Get the lexeme in i+2 position to skip the white-space
				Lexeme<PHPTokenType> className = lexemeProvider.getLexeme(i + 2);
				return new CallInfo(className.getText(), className.getEndingOffset());
			}
			if (PHPRegionTypes.PHP_TOKEN.equals(type))
			{
				if (")".equals(currentLexeme.getText())) //$NON-NLS-1$
				{
					level++;
				}
				else if ("(".equals(currentLexeme.getText())) //$NON-NLS-1$
				{
					if (level == 0)
					{
						Lexeme<PHPTokenType> function = findLexemeBackward(lexemeProvider, i - 1,
								PHPRegionTypes.PHP_STRING, new String[] { PHPRegionTypes.WHITESPACE,
										PHPRegionTypes.PHP_COMMENT, PHPRegionTypes.PHP_COMMENT_END,
										PHPRegionTypes.PHP_COMMENT_START });
						if (function == null)
						{
							return null;
						}
						return new CallInfo(function.getText(), function.getEndingOffset());
					}
					level--;
				}
			}
			// i--;
		}
		return null;
	}

	/**
	 * Computes context information about the PHP function parse node.
	 * 
	 * @param pn
	 *            - parse node.
	 * @param nameEndOffset
	 * @return context info.
	 */
	static IContextInformation computeArgContextInformation(PHPFunctionParseNode pn, int nameEndOffset)
	{
		StringBuffer bf = new StringBuffer();
		Parameter[] parameters = pn.getParameters();
		String[] parameterNames = new String[parameters.length];
		for (int a = 0; a < parameters.length; a++)
		{
			parameterNames[a] = parameters[a].getVariableName();
		}
		for (int a = 0; a < parameters.length; a++)
		{
			parameters[a].addLabel(bf);
			if (a != parameters.length - 1)
			{
				bf.append(", "); //$NON-NLS-1$
			}
		}
		String info = bf.toString();
		ContextInformation contextInformation = new ContextInformation(info, info);
		return new PHPContextInformationWrapper(contextInformation, nameEndOffset);
	}

	/**
	 * Computes context information about the PHP function element entry.
	 * 
	 * @param entry
	 *            - element entry.
	 * @return context info.
	 */
	static IContextInformation computeArgContextInformation(IElementEntry entry, int nameEndOffset)
	{
		FunctionPHPEntryValue value = (FunctionPHPEntryValue) entry.getValue();
		StringBuffer bf = new StringBuffer();

		// bf.append(ElementsIndexingUtils.getLastNameInPath(entry.getEntryPath()));
		Map<String, Set<Object>> parameters = value.getParameters();
		int i = 0;
		for (String parName : parameters.keySet())
		{
			bf.append('$');
			bf.append(parName);
			if (i != parameters.size() - 1)
			{
				bf.append(", "); //$NON-NLS-1$
			}
			i++;
		}
		String info = bf.toString();
		ContextInformation contextInformation = new ContextInformation(info, info);
		return new PHPContextInformationWrapper(contextInformation, nameEndOffset);
	}

	/**
	 * Computes the context information for the built-in PHP class constructors.
	 * 
	 * @param cn
	 * @param nameEndOffset
	 * @return Context information.
	 */
	static IContextInformation computeConstructorContextInformation(PHPClassParseNode cn, int nameEndOffset)
	{
		IParseNode[] children = cn.getChildren();
		for (IParseNode child : children)
		{
			if (child instanceof PHPFunctionParseNode)
			{
				PHPFunctionParseNode func = (PHPFunctionParseNode) child;
				// Return the first hit for a constructor.
				// This should be modified once we'll have multiple return
				// options (like in JDT)
				if ("__construct".equals(func.getNodeName()) //$NON-NLS-1$
						|| cn.getNodeName().equals(func.getNodeName()))
				{
					Parameter[] parameters = func.getParameters();
					if (parameters != null && parameters.length > 0)
					{
						return computeArgContextInformation(func, nameEndOffset);
					}
				}
			}
		}
		return null;
	}

	static String wrapString(String s, int maxWidth, String delimiter)
	{
		if (Display.getCurrent() == null)
		{
			return s;
		}
		StringReader sr = new StringReader(s);
		GC gc = new GC(Display.getCurrent());
		String result = ""; //$NON-NLS-1$
		LineBreakingReader r = new LineBreakingReader(sr, gc, maxWidth);

		try
		{
			String line = r.readLine();
			while (line != null)
			{
				result += line;
				line = r.readLine();
				if (line != null)
				{
					result += delimiter;
				}
			}
		}
		catch (IOException e)
		{
		}

		gc.dispose();

		return result;
	}

	/**
	 * A context information that implements {@link IContextInformationExtension} to provide accurate location for the
	 * function name end position. This is needed for an accurate computation of the argument position at the
	 * PHPContextInformationValidator (getCharCount)
	 * 
	 * @author Shalom
	 */
	static class PHPContextInformationWrapper implements IContextInformation, IContextInformationExtension
	{

		private ContextInformation contextInformation;
		private final int contextPosition;

		public PHPContextInformationWrapper(ContextInformation information, int contextPosition)
		{
			this.contextInformation = information;
			this.contextPosition = contextPosition;
		}

		public String getContextDisplayString()
		{
			return contextInformation.getContextDisplayString();
		}

		public Image getImage()
		{
			return contextInformation.getImage();
		}

		public String getInformationDisplayString()
		{
			return contextInformation.getContextDisplayString();
		}

		public int getContextInformationPosition()
		{
			return contextPosition;
		}
	}
}
