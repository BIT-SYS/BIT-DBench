package org.ita.neutrino.refactorings;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.ita.neutrino.codeparser.Environment;
import org.ita.neutrino.codeparser.ParserException;
import org.ita.neutrino.codeparser.astparser.ASTParser;
import org.ita.neutrino.codeparser.astparser.ASTSelection;
import org.ita.neutrino.eclipseaction.Activator;
import org.ita.neutrino.tparsers.abstracttestparser.AbstractTestParser;
import org.ita.neutrino.tparsers.abstracttestparser.TestBattery;
import org.ita.neutrino.tparsers.abstracttestparser.TestElement;
import org.ita.neutrino.tparsers.abstracttestparser.TestParserException;
import org.ita.neutrino.tparsers.abstracttestparser.TestSelection;
import org.ita.neutrino.tparsers.generictestparser.GenericTestParser;
import org.ita.neutrino.tparsers.junit3parser.JUnit3Parser;
import org.ita.neutrino.tparsers.junit4parser.JUnit4Parser;

public abstract class AbstractEclipseRefactoringCommandHandler extends AbstractHandler {
	
	private AbstractRefactoring refactoringObject;
	private RefactoringWizard refactoringWizard;
	
	public IWorkbenchWindow getWindow() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	}

	public ISelection getSelection() {
		return getWindow().getSelectionService().getSelection();
	}

	/**
	 * Deve devolver um nome amigável para a refatoração, esse valor será
	 * utilizado nos diálogos com o usuário.
	 * 
	 * @return
	 */
	protected abstract String getRefactoringName();

//	@Override
//	public void run() throws ActionException {
//		verifyPreConditions();
//
//		Environment environment = doCodeParsing();
//		
//		TestBattery battery = doTestParsing(environment);
//
//		refactoringObject = createRefactoringObject();
//
//		if (refactoringObject == null) {
//			throw new ActionException("Method \"" + getClass().getName() + ".createRefactoringObject()\" must return non null value.");
//		}
//
//		refactoringObject.setBattery(battery);
//		TestSelection selection = battery.getSelection();
//		TestElement<?> element = selection.getSelectedFragment();
//		refactoringObject.setTargetFragment(element);
//
//		verifyInitialConditions();
//
//		if (!prepareRefactoringObject()) {
//			return;
//		}
//
//		try {
//			refactoringObject.refactor();
//		} catch (RefactoringException e) {
//			throw new ActionException(e);
//		}
//	}
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {		
		verifyPreConditions();
		
		Environment environment = doCodeParsing();
	
		TestBattery battery = doTestParsing(environment);
		
		environment.beginModification();
		
		refactoringObject = createRefactoringObject();
		refactoringWizard = createRefactoringWizard(refactoringObject);
		
		if (refactoringObject == null) 
			throw new ExecutionException("Method \"" + getClass().getName() + ".createRefactoringObject()\" must return non null value.");
		

		refactoringObject.setBattery(battery);
		TestSelection selection = battery.getSelection();
		TestElement<?> element = selection.getSelectedFragment();
		refactoringObject.setTargetFragment(element);
		
		RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(refactoringWizard);
		
		try {
			operation.run(getWindow().getShell(), getRefactoringName());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	private void verifyPreConditions() throws ExecutionException {
		List<String> problems = checkPreConditions();

		if ((problems != null) && (problems.size() > 0)) {
			String message = RefactoringException.getMessageForProblemList(problems);

			MessageDialog.openWarning(null, getRefactoringName(), message);

			throw new ExecutionException(message);
		}
	}

	/**
	 * Permite fazer uma checagem prévia das condições no Eclipse antes de fazer
	 * qualquer outra coisa. Pode devolver null indicando que não houveram
	 * problemas. Essa checagem deve ser específica do Eclipse.
	 * 
	 * @return
	 */
	protected abstract List<String> checkPreConditions();

	private Environment doCodeParsing() throws ExecutionException {
		ASTParser codeParser = new ASTParser();

		try {
			// Retorna todo o código fonte existente no projeto e o passo para o
			// objeto codeParser
			codeParser.setCompilationUnits(RefactoringUtils.getAllWorkspaceCompilationUnits(null).toArray(new ICompilationUnit[0]));
		} catch (CoreException e) {
			throw new ExecutionException(e.getMessage());
		}

		ICompilationUnit activeCompilationUnit = getActiveCompilationUnit();
		codeParser.setActiveCompilationUnit(activeCompilationUnit);
		ASTSelection codeSelection = codeParser.getSelection();
		codeSelection.setSourceFile(activeCompilationUnit);

		ITextSelection textSelection = (ITextSelection) getSelection();
		codeSelection.setSelectionStart(textSelection.getOffset());
		codeSelection.setSelectionLength(textSelection.getLength());

		try {
			codeParser.parse();
		} catch (ParserException e) {
			throw new ExecutionException(e.getMessage());
		}

		return codeParser.getEnvironment();
	}

	protected AbstractTestParser instantiateParser() {
		return new GenericTestParser(
				new JUnit3Parser().asTestSuiteParser(),
				new JUnit4Parser().asTestSuiteParser());
	}

	private TestBattery doTestParsing(Environment environment) throws ExecutionException {
		AbstractTestParser testParser = instantiateParser();

		testParser.setEnvironment(environment);

		try {
			testParser.parse();
		} catch (TestParserException e) {
			throw new ExecutionException(e.getMessage());
		}

		return testParser.getBattery();
	}

	/**
	 * Deve instanciar e devolver o objeto de refatoração. Deve obrigatoriamente
	 * devolver uma instância não nula.
	 * 
	 * @return
	 */
	protected abstract AbstractRefactoring createRefactoringObject();
	
	protected abstract RefactoringWizard createRefactoringWizard(Refactoring refactoring);

//	private void verifyInitialConditions() throws ActionException {
//		List<String> errors = refactoringObject.checkInitialConditions();
//
//		// Verificar se errors veio nulo, checkInitialConditions pode
//		// devolver um valor nulo indicando que não houver erros
//
//		if (errors.size() > 0) {
//			String message = RefactoringException.getMessageForProblemList(errors);
//
//			MessageDialog.openWarning(null, getRefactoringName(), message);
//
//			throw new ActionException(message);
//		}
//	}

	/**
	 * Preparação final do objeto de refatoração. Deve devolver true caso a
	 * refatoração deva continuar. Se devolver false, nenhuma exceção é lançada.
	 * 
	 * @return
	 */
	protected boolean prepareRefactoringObject() {
		return true;
	}

	private ICompilationUnit getActiveCompilationUnit() {
		IWorkbench workbench = Activator.getDefault().getWorkbench();

		IWorkbenchWindow workbenchWindow = workbench.getActiveWorkbenchWindow();

		IWorkbenchPage page = workbenchWindow.getActivePage();

		IEditorPart editorPart = page.getActiveEditor();

		if (editorPart == null) {
			// Nenhuma janela de edição ativa
			return null;
		}

		IEditorInput editorInput = editorPart.getEditorInput();

		ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(editorInput);

		return (ICompilationUnit) typeRoot;
	}

}
