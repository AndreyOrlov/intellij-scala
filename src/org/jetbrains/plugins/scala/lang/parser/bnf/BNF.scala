package org.jetbrains.plugins.scala.lang.parser.bnf

import com.intellij.psi.tree.TokenSet, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.tree.IElementType, org.jetbrains.plugins.scala.lang.parser._

object BNF {

  val literals: Array[IElementType] =
    Array.apply(ScalaTokenTypes.tINTEGER,
        ScalaTokenTypes.tFLOAT,
        ScalaTokenTypes.kTRUE,
        ScalaTokenTypes.kFALSE,
        ScalaTokenTypes.tCHAR,
        ScalaTokenTypes.tSTRING,
        ScalaTokenTypes.tSYMBOL,
        ScalaTokenTypes.kNULL)

  val tLITERALS = TokenSet.create(literals)

  val firstLiteral: TokenSet = TokenSet.create(literals)

  val tSIMPLE_FIRST: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
      ScalaTokenTypes.kTHIS,
      ScalaTokenTypes.kSUPER,
      ScalaTokenTypes.tLPARENTHESIS,
      ScalaTokenTypes.kNEW))

  val tPREFIXES: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tPLUS,
      ScalaTokenTypes.tMINUS,
      ScalaTokenTypes.tTILDA,
      ScalaTokenTypes.tNOT))

  /*********** LAST **************/
  var lastTemplateStat = TokenSet.create(Array(ScalaTokenTypes.tRBRACE))


  /********************************************************/
  /*********************** FIRSTS *************************/

  val firstPath: TokenSet = TokenSet.create(Array(ScalaTokenTypes.kTHIS,
      ScalaTokenTypes.kSUPER,
      ScalaTokenTypes.tIDENTIFIER))

  val firstStableId: TokenSet = TokenSet.create(Array(ScalaTokenTypes.kTHIS,
      ScalaTokenTypes.kSUPER,
      ScalaTokenTypes.tIDENTIFIER))

  val firstXmlPattern = TokenSet.create(Array())

  val firstPattern2: TokenSet = TokenSet.orSet(Array(
    firstLiteral,
    TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
      ScalaTokenTypes.tUNDER,
      ScalaTokenTypes.tLPARENTHESIS,
      ScalaTokenTypes.tLBRACE)),
      firstXmlPattern
  ))

  val firstXmlExpr: TokenSet = TokenSet.create(Array())

  val firstMethodClosure: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tDOT))

  val firstSimpleExpr: TokenSet = TokenSet.orSet(Array(
      firstLiteral,
      firstPath,
      TokenSet.create(Array(
      ScalaTokenTypes.tLBRACE,
      ScalaTokenTypes.tLPARENTHESIS,
      ScalaTokenTypes.kNEW)),
      firstXmlExpr))

  val firstPrefixExpr: TokenSet = TokenSet.orSet(Array(firstSimpleExpr,
      TokenSet.create(Array(ScalaTokenTypes.tMINUS,
          ScalaTokenTypes.tPLUS,
          ScalaTokenTypes.tTILDA,
          ScalaTokenTypes.tNOT,
          ScalaTokenTypes.tAND))))

  val firstInfixExpr: TokenSet = firstPrefixExpr

  val firstPostfixExpr: TokenSet = firstInfixExpr

  val firstExpr1: TokenSet = TokenSet.orSet(Array(
      TokenSet.create(Array(
      ScalaTokenTypes.kIF,
      ScalaTokenTypes.kTRY,
      ScalaTokenTypes.kWHILE,
      ScalaTokenTypes.kDO,
      ScalaTokenTypes.kFOR,
      ScalaTokenTypes.kTHROW,
      ScalaTokenTypes.kRETURN)),
      TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)),
      firstSimpleExpr,
      firstPostfixExpr,
      firstMethodClosure))

  val firstBindings = TokenSet.create(Array(ScalaTokenTypes.tLBRACE))

  val firstExpr: TokenSet = TokenSet.orSet(Array(
      firstBindings,
      TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)),
      firstExpr1))


  val firstArgumentExprs: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tLBRACE,
      ScalaTokenTypes.tLPARENTHESIS))


  val firstSimpleType: TokenSet = TokenSet.orSet(Array(
      firstStableId,
      firstPath,
      TokenSet.create(Array(ScalaTokenTypes.tLPARENTHESIS))
      ))

  val firstType1: TokenSet = firstSimpleType

  val firstType: TokenSet = TokenSet.orSet(Array(
      firstType1,
      TokenSet.create(Array(ScalaTokenTypes.tLBRACE))
      ))

  /********************************************************************************************/
  /********************************* Import, Attribute and Modifier ***************************/

  val firstImport = TokenSet.create(Array(ScalaTokenTypes.kIMPORT))

  val firstImportSelector = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  val firstLocalModifier = TokenSet.create(Array(ScalaTokenTypes.kABSTRACT,
      ScalaTokenTypes.kFINAL,
      ScalaTokenTypes.kIMPLICIT,
      ScalaTokenTypes.kSEALED,
      ScalaTokenTypes.kLAZY))

  val firstLocalModifierWithoutImplicit = TokenSet.create(Array(ScalaTokenTypes.kABSTRACT,
      ScalaTokenTypes.kFINAL,
      ScalaTokenTypes.kSEALED,
      ScalaTokenTypes.kLAZY))


  val firstAccessModifier: TokenSet = TokenSet.create(Array(ScalaTokenTypes.kPRIVATE,
      ScalaTokenTypes.kPROTECTED))


  val firstModifierWithoutImplicit: TokenSet = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.kOVERRIDE)),
      firstAccessModifier,
      firstLocalModifierWithoutImplicit))

  val firstModifier: TokenSet = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.kOVERRIDE)),
      firstAccessModifier,
      firstLocalModifier))

  val firstAttributeClause: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tLSQBRACKET))


  /************* parameters ***************/

  val firstTypeParam = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  val firstVariantTypeParam = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.tPLUS,
      ScalaTokenTypes.tMINUS)),
      firstTypeParam))

  val firstFunTypeParam = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tLSQBRACKET))

  val firstTypeParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tLSQBRACKET))

  val firstClassTypeParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tLSQBRACKET))


  val firstParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tLPARENTHESIS))

  val firstClassParamClause = TokenSet.create(Array(ScalaTokenTypes.tLINE_TERMINATOR,
      ScalaTokenTypes.tLPARENTHESIS))


  val firstParamClauses = TokenSet.orSet(Array(firstParamClause,
      TokenSet.create(Array(ScalaTokenTypes.tLPARENTHESIS))))


  val firstClassParamClauses = TokenSet.orSet(Array(firstClassParamClause,
      TokenSet.create(Array(ScalaTokenTypes.tLPARENTHESIS))))


  val firstParamType = TokenSet.orSet(Array(firstType,
      TokenSet.create(Array(ScalaTokenTypes.tFUNTYPE))))

  val firstParam = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  val firstClassParam = TokenSet.orSet(Array(firstModifierWithoutImplicit,
      TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER,
          ScalaTokenTypes.kVAR,
          ScalaTokenTypes.kVAL)),
      firstParam))

  /*******************************************************************************/
  /********************************** Def and Dcl ********************************/

  val firstFunSig: TokenSet = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))


  val firstSelfInvocation: TokenSet = TokenSet.create(Array(ScalaTokenTypes.kTHIS))

  val firstConstrExpr: TokenSet = TokenSet.orSet(Array(firstSelfInvocation,
      TokenSet.create(Array(ScalaTokenTypes.tLBRACE))))

  val firstFunDef = firstFunSig

  val firstTmplDef = TokenSet.create(Array(ScalaTokenTypes.kCASE,
      ScalaTokenTypes.kCLASS,
      ScalaTokenTypes.kOBJECT,
      ScalaTokenTypes.kTRAIT))

  val firstDclDefKeywords = TokenSet.create(Array(ScalaTokenTypes.kVAL,
      ScalaTokenTypes.kVAR,
      ScalaTokenTypes.kDEF,
      ScalaTokenTypes.kTYPE))


  val firstBlockStat: TokenSet = TokenSet.orSet(Array(firstImport,
      firstLocalModifier,
      firstTmplDef,
      firstExpr1,
      firstDclDefKeywords))

  val firstResultExpr: TokenSet = TokenSet.orSet(Array(firstBindings,
      firstExpr1,
      TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))))

  val firstBlock: TokenSet = TokenSet.orSet(Array(firstBlockStat,
      firstResultExpr))

  val firstDef = TokenSet.orSet(Array(firstDclDefKeywords,
      firstTmplDef))

  val firstTypeDef = TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))

  /* it uses because of when we parse TemplateStat we don't know beforehand what we parse: dcl or def. If we get '= expr'
   * this means construction is Definition, else construction is Declaration
   */

  val firstDclDef = firstDef

  val firstDcl = firstDclDefKeywords

  val firstTemplateStat = TokenSet.orSet(Array(firstImport,
      firstAttributeClause,
      firstModifier,
      firstDclDef,
      firstExpr))

  val firstConstr = firstStableId

  val firstTemplateParents = firstConstr

  val firstTemplateBody =  TokenSet.create(Array(ScalaTokenTypes.tLBRACE))

  val firstClassTemplate = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.kEXTENDS,
      ScalaTokenTypes.tLINE_TERMINATOR)),
      firstTemplateBody))

  val firstTraitTemplate = firstClassTemplate

  val firstMixinParents = firstSimpleType

  /*******************************************************************************/
  /********************************** Top Statement ******************************/
  /*******************************************************************************/

  val firstPackaging = TokenSet.create(Array(ScalaTokenTypes.kPACKAGE))

  val firstTopStat = TokenSet.orSet(Array(firstAttributeClause,
      firstModifier,
      firstTmplDef,
      firstImport,
      firstPackaging))

  val firstTopStatSeq = TokenSet.orSet(Array(ScalaTokenTypes.STATEMENT_SEPARATORS,
      firstTopStat))
}