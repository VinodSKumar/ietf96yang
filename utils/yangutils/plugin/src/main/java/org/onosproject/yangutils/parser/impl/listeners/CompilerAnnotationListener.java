/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.yangutils.parser.impl.listeners;

import java.util.List;

import org.onosproject.yangutils.datamodel.YangAtomicPath;
import org.onosproject.yangutils.datamodel.YangCompilerAnnotation;
import org.onosproject.yangutils.datamodel.YangNode;
import org.onosproject.yangutils.datamodel.exceptions.DataModelException;
import org.onosproject.yangutils.datamodel.utils.Parsable;
import org.onosproject.yangutils.linker.impl.YangResolutionInfoImpl;
import org.onosproject.yangutils.parser.antlrgencode.GeneratedYangParser;
import org.onosproject.yangutils.parser.exceptions.ParserException;
import org.onosproject.yangutils.parser.impl.TreeWalkListener;

import static org.onosproject.yangutils.datamodel.utils.DataModelUtils.addResolutionInfo;
import static org.onosproject.yangutils.datamodel.utils.YangConstructType.COMPILER_ANNOTATION_DATA;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorLocation.ENTRY;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorLocation.EXIT;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorMessageConstruction
        .constructExtendedListenerErrorMessage;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorMessageConstruction
        .constructListenerErrorMessage;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorType.INVALID_CONTENT;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorType.INVALID_HOLDER;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorType.MISSING_CURRENT_HOLDER;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorType.MISSING_HOLDER;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerErrorType.UNHANDLED_PARSED_DATA;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerUtil.getValidAbsoluteSchemaNodeId;
import static org.onosproject.yangutils.parser.impl.parserutils.ListenerValidation.checkStackIsNotEmpty;
import static org.onosproject.yangutils.utils.UtilConstants.COLON;

/*
 * Reference: RFC6020 and YANG ANTLR Grammar
 *
 * ABNF grammar as per RFC6020
 *  container-stmt      = container-keyword sep identifier-arg-str optsep
 *                        (";" /
 *                         "{" stmtsep
 *                             ;; these stmts can appear in any order
 *                             [when-stmt stmtsep]
 *                             *(if-feature-stmt stmtsep)
 *                             *(must-stmt stmtsep)
 *                             [presence-stmt stmtsep]
 *                             [config-stmt stmtsep]
 *                             [status-stmt stmtsep]
 *                             [description-stmt stmtsep]
 *                             [reference-stmt stmtsep]
 *                             *((typedef-stmt /
 *                                grouping-stmt) stmtsep)
 *                             *(data-def-stmt stmtsep)
 *                         "}")
 *
 * ANTLR grammar rule
 *  containerStatement : CONTAINER_KEYWORD identifier
 *                   (STMTEND | LEFT_CURLY_BRACE (whenStatement | ifFeatureStatement | mustStatement |
 *                   presenceStatement | configStatement | statusStatement | descriptionStatement |
 *                   referenceStatement | typedefStatement | groupingStatement
 *                    | dataDefStatement)* RIGHT_CURLY_BRACE);
 */

/**
 * Represents listener based call back function corresponding to the "container"
 * rule defined in ANTLR grammar file for corresponding ABNF rule in RFC 6020.
 */
public final class CompilerAnnotationListener {

    /**
     * Creates a new container listener.
     */
    private CompilerAnnotationListener() {
    }

    /**
     * It is called when parser receives an input matching the grammar rule
     * (container), performs validation and updates the data model tree.
     *
     * @param listener listener's object
     * @param ctx      context object of the grammar rule
     */
    public static void processCompilerAnnotationEntry(TreeWalkListener listener,
            GeneratedYangParser.CompilerAnnotationStatementContext ctx) {
        // Check for stack to be non empty.
        checkStackIsNotEmpty(listener, MISSING_HOLDER, COMPILER_ANNOTATION_DATA, ctx.string().getText(), ENTRY);

        YangCompilerAnnotation compilerAnnotation = new YangCompilerAnnotation();
        compilerAnnotation.setPath(ctx.string().getText());

        // Validate augment argument string
        List<YangAtomicPath> targetNodes = getValidAbsoluteSchemaNodeId(ctx.string().getText(),
                COMPILER_ANNOTATION_DATA, ctx);

        compilerAnnotation.setAtomicPathList(targetNodes);

        //set prefix
        String[] splitArray = ctx.COMPILER_ANNOTATION().getText().split(COLON);
        if (splitArray.length != 2) {
            throw new ParserException(constructListenerErrorMessage(INVALID_CONTENT, COMPILER_ANNOTATION_DATA,
                    ctx.string().getText(), ENTRY));
        }
        compilerAnnotation.setPrefix(splitArray[0]);

        int line = ctx.getStart().getLine();
        int charPositionInLine = ctx.getStart().getCharPositionInLine();

        Parsable curData = listener.getParsedDataStack().peek();
        switch (curData.getYangConstructType()) {
            case MODULE_DATA:
                break;
            case SUB_MODULE_DATA:
                break;
            default:
                throw new ParserException(constructListenerErrorMessage(INVALID_HOLDER, COMPILER_ANNOTATION_DATA,
                        ctx.string().getText(), ENTRY));
        }

        // Add resolution information to the list
        YangResolutionInfoImpl resolutionInfo = new YangResolutionInfoImpl<YangCompilerAnnotation>(compilerAnnotation,
                (YangNode) curData, line,
                charPositionInLine);
        addToResolutionList(resolutionInfo, ctx);

        listener.getParsedDataStack().push(compilerAnnotation);
    }

    /**
     * It is called when parser exits from grammar rule (container), it perform
     * validations and updates the data model tree.
     *
     * @param listener listener's object
     * @param ctx      context object of the grammar rule
     */
    public static void processCompilerAnnotationExit(TreeWalkListener listener,
            GeneratedYangParser.CompilerAnnotationStatementContext ctx) {

        checkStackIsNotEmpty(listener, MISSING_HOLDER, COMPILER_ANNOTATION_DATA, ctx.string().getText(), EXIT);
        if (!(listener.getParsedDataStack().peek() instanceof YangCompilerAnnotation)) {
            throw new ParserException(constructListenerErrorMessage(MISSING_CURRENT_HOLDER, COMPILER_ANNOTATION_DATA,
                    ctx.string().getText(), EXIT));
        }
        listener.getParsedDataStack().pop();
    }

    /**
     * Add to resolution list.
     *
     * @param resolutionInfo resolution information.
     * @param ctx            context object of the grammar rule
     */
    private static void addToResolutionList(YangResolutionInfoImpl<YangCompilerAnnotation> resolutionInfo,
            GeneratedYangParser.CompilerAnnotationStatementContext ctx) {

        try {
            addResolutionInfo(resolutionInfo);
        } catch (DataModelException e) {
            throw new ParserException(constructExtendedListenerErrorMessage(UNHANDLED_PARSED_DATA,
                    COMPILER_ANNOTATION_DATA, ctx.COMPILER_ANNOTATION().getText(), ENTRY, e.getMessage()));
        }
    }
}
