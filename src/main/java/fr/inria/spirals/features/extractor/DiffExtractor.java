package fr.inria.spirals.features.extractor;

import fr.inria.spirals.entities.Change;
import fr.inria.spirals.entities.ChangeAnalyze;
import fr.inria.spirals.entities.Changes;
import fr.inria.spirals.features.ExtractorResults;
import fr.inria.spirals.features.analyzer.DiffAnalyzer;
import fr.inria.spirals.features.analyzer.ElementAnalyzer;
import spoon.Launcher;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.CtScanner;

import java.util.List;

/**
 * Created by tdurieux
 */
public class DiffExtractor extends AbstractExtractor {

	public DiffExtractor(String oldSourcePath, String newSourcePath, String diffPath) {
		super(oldSourcePath, newSourcePath, diffPath);
	}

	public ExtractorResults extract() {
		DiffAnalyzer diffAnalyzer = new DiffAnalyzer(diffPath);
		Changes changes = diffAnalyzer.analyze();

		Launcher oldSpoon = null;
		if (!changes.getChangedOldFiles().isEmpty()) {
			oldSpoon = initSpoon(buggySourcePath, changes.getChangedOldFiles());
		}

		final ChangeAnalyze oldAnalyze = getChangeAnalyze(changes.getOldChanges(), oldSpoon);

		Launcher newSpoon = null;
		if (!changes.getChangedNewFiles().isEmpty()) {
			newSpoon = initSpoon(fixedSourcePath, changes.getChangedNewFiles());
		}

		final ChangeAnalyze newAnalyze = getChangeAnalyze(changes.getNewChanges(), newSpoon);

		ExtractorResults extractorResults = new ExtractorResults(oldAnalyze, newAnalyze);
		extractorResults.setNbFiles(diffAnalyzer.getNbFiles());
		return extractorResults;
	}

	private ChangeAnalyze getChangeAnalyze(final List<Change> changes, Launcher spoon) {
		final ChangeAnalyze analyze = new ChangeAnalyze();

		for (int i = 0; i < changes.size(); i++) {
			Change change = changes.get(i);
			analyze.incNbChange(change.getLength());
			if (spoon != null) {
				getChangeAnalyze(change, spoon, analyze);
			}
		}
		return analyze;
	}

	private void getChangeAnalyze(final Change change, Launcher spoon,
			final ChangeAnalyze analyze) {
		spoon.getModel().getRootPackage().accept(new CtScanner() {
			@Override
			public void scan(CtElement e) {
				if (e == null) {
					super.scan(e);
					return;
				}
				if (e instanceof CtType) {
					if (e.getPosition().getFile().getPath().contains(change.getFile())) {
						super.scan(e);
					}
				} else {
					if (e.getPosition().getFile() != null && e.getPosition().getFile().getPath().contains(change.getFile())) {
						int elementLine = e.getPosition().getLine();
						int endLine = e.getPosition().getEndLine();
						if (e instanceof CtTry && elementLine >= change.getLine() && elementLine <= change.getEndLine()) {
							analyze.incNbTry();
						}
						if (elementLine >= change.getLine() && endLine <= change.getEndLine()) {
							new ElementAnalyzer(e).analyze(analyze);
						} else if ((elementLine <= change.getLine() && endLine >= change.getEndLine()) ||
								elementLine >= change.getLine() && elementLine < change.getEndLine() && endLine >= change.getEndLine()) {
							super.scan(e);
						}
					} else {
						super.scan(e);
					}
				}
			}
		});
	}

	private Launcher initSpoon(String source, List<String> files) {
		Launcher spoon = new Launcher();
		spoon.getEnvironment().setNoClasspath(true);
		spoon.getEnvironment().setCommentEnabled(true);
		for (int i = 0; i < files.size(); i++) {
			String file = files.get(i);
			if (!file.endsWith(".java")) {
				continue;
			}
			if (!file.startsWith("/")) {
				file = "/" + file;
			}
			if (!file.contains(source)) {
				file = source + file;
			}
			spoon.addInputResource(file);
		}
		spoon.buildModel();
		return spoon;
	}

}