package com.example.refineplugin.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.EmptyDeprecationsProvider
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.InlineStatus
import org.jetbrains.kotlin.fir.declarations.builder.buildAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildReturnExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedNamedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagWithFixedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.Variance

private val REFINE_ANNOTATION = ClassId(FqName("com.example"), Name.identifier("RefineMe"))
private val BOX_CLASS = ClassId(FqName("com.example"), Name.identifier("Box"))

data object RefinePluginKey : GeneratedDeclarationKey()

@OptIn(FirExtensionApiInternals::class, SymbolInternals::class)
class RefineCallExtension(session: FirSession) : FirFunctionCallRefinementExtension(session) {

    override fun intercept(callInfo: CallInfo, symbol: FirNamedFunctionSymbol): CallReturnType? {
        if (!symbol.hasAnnotation(REFINE_ANNOTATION, session)) return null

        System.err.println("[RefineCallExtension] intercept fired for ${symbol.callableId}")

        // Build a synthetic local class `RefinedSchema`.
        //
        // NOTE: do NOT put a hand-built primary constructor into `declarations` here.
        // The class has `FirDeclarationOrigin.Plugin` (origin.generated == true), and for
        // such classes `FirDeclaredMemberScopeProvider.createDeclaredMemberScope` builds the
        // declared member scope ONLY from FirDeclarationGenerationExtension results
        // (FirGeneratedClassDeclaredMemberScope with scopeForGeneratedClass = true) and
        // ignores `klass.declarations`. fir2ir's `processClassMembers` looks the primary
        // constructor up through that scope (`primaryConstructorIfAny(session)`), so a
        // constructor that only lives in `declarations` never gets an IR constructor cached,
        // and `ClassMemberGenerator.convertClassContent` later NPEs on
        // `getCachedIrConstructorSymbol(it)!!`. The constructor is instead supplied by
        // `RefineSchemaConstructorGenerator` below (same pattern as kotlin-dataframe's
        // TokenContentGenerator).
        val schemaId = localClassId(Name.identifier("RefinedSchema"))
        val schemaSymbol = FirRegularClassSymbol(schemaId)
        val anyType = session.builtinTypes.anyType.coneType

        val schemaClass = buildRegularClass {
            // 2.4.20+: every local declaration injected into a source file must carry a
            // distinct source element; use PluginGenerated.Custom with a per-class marker.
            source = callInfo.callSite.source?.fakeElement(
                KtFakeSourceElementKind.PluginGenerated.Custom(
                    RefineSourceElementKind.SchemaClass(schemaId.shortClassName.asString()),
                ),
            )
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Plugin(RefinePluginKey)
            status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.FINAL, EffectiveVisibility.Local)
            deprecationsProvider = EmptyDeprecationsProvider
            classKind = ClassKind.CLASS
            scopeProvider = FirKotlinScopeProvider()
            superTypeRefs += buildResolvedTypeRef { coneType = anyType }
            name = schemaId.shortClassName
            this.symbol = schemaSymbol
        }

        // Build refined return type: Box<RefinedSchema>.
        val boxLookupTag = ConeClassLikeLookupTagImpl(BOX_CLASS)
        val refinedTypeRef = buildResolvedTypeRef {
            coneType = ConeClassLikeTypeImpl(
                boxLookupTag,
                arrayOf(
                    ConeClassLikeTypeImpl(
                        ConeClassLikeLookupTagWithFixedSymbol(schemaId, schemaSymbol),
                        emptyArray(),
                        isMarkedNullable = false,
                    ),
                ),
                isMarkedNullable = false,
            )
        }

        return CallReturnType(refinedTypeRef) { newSymbol ->
            session.refineCallDataStorage.callDataCache.getValue(newSymbol, RefineCallData(schemaClass))
        }
    }

    override fun transform(call: FirFunctionCall, originalSymbol: FirNamedFunctionSymbol): FirFunctionCall {
        if (call.calleeReference !is FirResolvedNamedReference) return call

        val resolvedRun = findRun() ?: return call
        val parameter = resolvedRun.valueParameterSymbols[0]

        val returnType = call.resolvedType
        val originalSource = call.calleeReference.source

        val symbol = call.calleeReference.resolved?.toResolvedNamedFunctionSymbol() ?: return call
        val callData = session.refineCallDataStorage.callDataCache.getValue(symbol)
        val schemaClass = callData.schema

        System.err.println("[RefineCallExtension] transform fired for ${originalSymbol.callableId}, returnType=$returnType")

        // Restore the calleeReference to point back at the original symbol (the cloned one is discarded).
        call.transformCalleeReference(object : FirTransformer<Nothing?>() {
            override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
                return if (element is FirResolvedNamedReference) {
                    @Suppress("UNCHECKED_CAST")
                    buildResolvedNamedReference {
                        this.name = element.name
                        resolvedSymbol = originalSymbol
                    } as E
                } else {
                    element
                }
            }
        }, null)

        // Anchor the generated class to the original call source for ownsSymbol/anchorElement.
        schemaClass.anchor = call.source
        schemaClass.generatedClasses = listOf(schemaClass.symbol)

        // Build the lambda block: { class RefinedSchema; box() }
        val lambdaArgument = buildAnonymousFunctionExpression {
            val fSymbol = FirAnonymousFunctionSymbol()
            val target = FirFunctionTarget(null, isLambda = true)
            isTrailingLambda = true
            anonymousFunction = buildAnonymousFunction {
                source = call.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated.Default)
                resolvePhase = FirResolvePhase.BODY_RESOLVE
                moduleData = session.moduleData
                origin = FirDeclarationOrigin.Plugin(RefinePluginKey)
                status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.FINAL, EffectiveVisibility.Local)
                deprecationsProvider = EmptyDeprecationsProvider
                returnTypeRef = buildResolvedTypeRef { coneType = returnType }
                body = buildBlock {
                    this.coneTypeOrNull = returnType
                    // The schema class is added as a statement so it becomes a real
                    // local declaration in the IR tree (otherwise JvmInventNamesForLocalClasses
                    // never visits it). See RESULT.md.
                    statements += schemaClass
                    statements += buildReturnExpression {
                        result = call
                        this.target = target
                    }
                }
                this.symbol = fSymbol
                isLambda = true
                hasExplicitParameterList = false
                typeRef = buildResolvedTypeRef {
                    coneType = ConeClassLikeTypeImpl(
                        ConeClassLikeLookupTagImpl(ClassId(FqName("kotlin"), Name.identifier("Function0"))),
                        typeArguments = arrayOf(returnType),
                        isMarkedNullable = false,
                    )
                }
                invocationKind = EventOccurrencesRange.EXACTLY_ONCE
                inlineStatus = InlineStatus.Inline
            }.also { target.bind(it) }
        }

        // Build kotlin.run<R>(block) call.
        val newCall = buildFunctionCall {
            this.coneTypeOrNull = returnType
            typeArguments += buildTypeProjectionWithVariance {
                typeRef = buildResolvedTypeRef { coneType = returnType }
                variance = Variance.INVARIANT
            }
            argumentList = buildResolvedArgumentList(original = null, linkedMapOf(lambdaArgument to parameter.fir))
            calleeReference = buildResolvedNamedReference {
                source = originalSource
                name = Name.identifier("run")
                resolvedSymbol = resolvedRun
            }
        }
        return newCall
    }

    override fun ownsSymbol(symbol: FirRegularClassSymbol): Boolean = symbol.anchor != null

    override fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement = symbol.anchor!!

    override fun restoreSymbol(call: FirFunctionCall, name: Name): FirRegularClassSymbol? {
        val typeArg = call.resolvedType.typeArguments.firstOrNull() as? ConeClassLikeType ?: return null
        val regularClassSymbol = typeArg.toRegularClassSymbol(session) ?: return null
        return regularClassSymbol.generatedClasses?.find { it.name == name }
    }

    private fun localClassId(name: Name): ClassId =
        ClassId(CallableId.PACKAGE_FQ_NAME_FOR_LOCAL, FqName.ROOT.child(name), isLocal = true)

    private fun findRun(): FirFunctionSymbol<*>? {
        return session.symbolProvider
            .getTopLevelFunctionSymbols(FqName("kotlin"), Name.identifier("run"))
            .firstOrNull { it.valueParameterSymbols.size == 1 && it.receiverParameterSymbol == null }
    }
}

/**
 * Supplies the primary constructor of every `RefinedSchema` class emitted by [RefineCallExtension].
 *
 * Classes with a `FirDeclarationOrigin.Plugin` origin get their declared member scope exclusively
 * from [FirDeclarationGenerationExtension]s, so this is the only channel through which fir2ir can
 * discover (and cache) the constructor before `convertClassContent` asks for it.
 */
class RefineSchemaConstructorGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private fun FirClassSymbol<*>.isRefinedSchema(): Boolean =
        isLocal && (origin as? FirDeclarationOrigin.Plugin)?.key == RefinePluginKey

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> =
        if (classSymbol.isRefinedSchema()) setOf(SpecialNames.INIT) else emptySet()

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        if (!context.owner.isRefinedSchema()) return emptyList()
        return listOf(
            createConstructor(context.owner, RefinePluginKey, isPrimary = true, generateDelegatedNoArgConstructorCall = true).symbol,
        )
    }
}

/** Markers for `KtFakeSourceElementKind.PluginGenerated.Custom`; data classes give stable equals/hashCode/toString. */
private sealed class RefineSourceElementKind {
    data class SchemaClass(val name: String) : RefineSourceElementKind()
}

class RefineCallDataStorage(session: FirSession) : FirExtensionSessionComponent(session) {
    val callDataCache =
        session.firCachesFactory.createCache<FirNamedFunctionSymbol, RefineCallData, RefineCallData?> { symbol, context ->
            context ?: error("context not provided for $symbol")
        }
}

val FirSession.refineCallDataStorage: RefineCallDataStorage by FirSession.sessionComponentAccessor()

class RefineCallData(val schema: FirRegularClass)

private object RefineAnchorKey : FirDeclarationDataKey()
private var FirClass.anchor: KtSourceElement? by FirDeclarationDataRegistry.data(RefineAnchorKey)
private val FirRegularClassSymbol.anchor: KtSourceElement? by FirDeclarationDataRegistry.symbolAccessor(RefineAnchorKey)

private object RefineGeneratedClassesKey : FirDeclarationDataKey()
private var FirClass.generatedClasses: List<FirRegularClassSymbol>? by FirDeclarationDataRegistry.data(RefineGeneratedClassesKey)
private val FirRegularClassSymbol.generatedClasses: List<FirRegularClassSymbol>? by FirDeclarationDataRegistry.symbolAccessor(RefineGeneratedClassesKey)
