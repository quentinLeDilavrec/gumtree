package fr.labri.gumtree.client;

import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import fr.labri.gumtree.client.ui.swing.SwingDiff;
import fr.labri.gumtree.client.ui.web.WebDiff;
import fr.labri.gumtree.matchers.composite.Matcher;
import fr.labri.gumtree.tree.Tree;

public abstract class DiffClient {
	
	public static void main(String[] args) {
		DiffOptions diffOptions = new DiffOptions();
		CmdLineParser parser = new CmdLineParser(diffOptions);
		try {
			parser.parseArgument(args);
			DiffClient client;
			if ("swing".equals(diffOptions.getOutput())) client = new SwingDiff(diffOptions);
			else client = new WebDiff(diffOptions);
			client.start();
		} catch (CmdLineException e) {
			e.printStackTrace();
		}
	}
	
	protected DiffOptions diffOptions;	
	
	public DiffClient(DiffOptions diffOptions) {
		this.diffOptions = diffOptions;
	}
	
	public abstract void start();
	
	protected Matcher getMatcher() {
		return MatcherFactory.createMatcher(getSrcTree(), getDstTree(), diffOptions.getMatcher());
	}
	
	private Tree getSrcTree() {
		return getTree(diffOptions.getSrc());
	}
	
	private Tree getDstTree() {
		return getTree(diffOptions.getDst());
	}
	
	private Tree getTree(String file) {
		try {
			Tree t = TreeGeneratorRegistry.getInstance().getTree(file, diffOptions.getGenerators());
			return t;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
}