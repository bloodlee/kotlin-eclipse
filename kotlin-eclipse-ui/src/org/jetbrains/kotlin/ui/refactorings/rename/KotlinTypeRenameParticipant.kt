/*******************************************************************************
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *******************************************************************************/
package org.jetbrains.kotlin.ui.refactorings.rename

import org.eclipse.ltk.core.refactoring.participants.RenameParticipant
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.ltk.core.refactoring.Change
import kotlin.properties.Delegates
import org.eclipse.jdt.core.IType
import java.util.ArrayList
import org.eclipse.ltk.core.refactoring.CompositeChange
import org.jetbrains.kotlin.ui.search.KotlinQueryParticipant
import org.eclipse.search.ui.text.Match
import org.eclipse.jdt.ui.search.ElementQuerySpecification
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory
import org.eclipse.core.runtime.NullProgressMonitor
import org.jetbrains.kotlin.ui.search.KotlinElementMatch
import org.jetbrains.kotlin.core.builder.KotlinPsiManager
import org.jetbrains.kotlin.eclipse.ui.utils.EditorUtil
import org.eclipse.ltk.core.refactoring.TextFileChange
import org.jetbrains.kotlin.eclipse.ui.utils.getTextDocumentOffset
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments
import org.eclipse.ltk.core.refactoring.participants.RenameArguments

public class KotlinTypeRenameParticipant : RenameParticipant() {
    lateinit var element: IType
    lateinit var newName: String
    
    val changes = arrayListOf<Change>()
        
    override fun initialize(element: Any): Boolean {
        this.element = if (element is KotlinLightType) element.originElement else element as IType
        changes.clear()
        return true
    }
    
    override fun initialize(processor: RefactoringProcessor, element: Any?, arguments: RefactoringArguments): Boolean {
        if (arguments is RenameArguments) {
            newName = arguments.getNewName()
        }
        
        return super.initialize(processor, element, arguments)
    }
    
    override fun checkConditions(pm: IProgressMonitor, context: CheckConditionsContext): RefactoringStatus? {
        val kotlinQueryParticipant = KotlinQueryParticipant()
        val matches = arrayListOf<Match>()
        val factory = JavaSearchScopeFactory.getInstance()
        val querySpecification = ElementQuerySpecification(
                element, 
                IJavaSearchConstants.REFERENCES,
                factory.createWorkspaceScope(false),
                factory.getWorkspaceScopeDescription(false))
        
        kotlinQueryParticipant.search({ matches.add(it) }, querySpecification, NullProgressMonitor())
        
        matches
            .map { createTextChange(it) }
            .filterNotNull()
            .forEach { changes.add(it) }
        
        return RefactoringStatus() // TODO: add corresponding refactoring status
    }
    
    override fun getName() = "Kotlin Type Rename Participant"
    
    override fun createChange(pm: IProgressMonitor): Change {
        return CompositeChange("Changes in Kotlin", changes.toTypedArray())
    }
    
    private fun createTextChange(match: Match): Change? {
        if (match !is KotlinElementMatch) return null
        
        val jetElement = match.jetElement
        
        val eclipseFile = KotlinPsiManager.getEclispeFile(jetElement.getContainingJetFile())
        if (eclipseFile == null) return null
        
        val document = EditorUtil.getDocument(eclipseFile) // TODO: make workaround here later
        
        val change = TextFileChange("Rename Kotlin reference", eclipseFile)
        change.setEdit(ReplaceEdit(jetElement.getTextDocumentOffset(document), jetElement.getTextLength(), newName))
        
        return change
    }
}