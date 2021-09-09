package com.github.takemikami.sparqlmarkdown;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFParser;


public class SparqlMarkdown {

  private static final Pattern SPARQL_BEGIN = Pattern.compile("^```\s*sparql\s*$");
  private static final Pattern SPARQL_END = Pattern.compile("^```\s*$");
  private static final String RESULT_BEGIN_COMMENT = "<!-- start of sparql result -->";
  private static final String RESULT_END_COMMENT = "<!-- end of sparql result -->";
  private static final Pattern RESULT_BEGIN = Pattern.compile("^" + RESULT_BEGIN_COMMENT + "$");
  private static final Pattern RESULT_END = Pattern.compile("^" + RESULT_END_COMMENT + "$");

  public static void main(String[] args) throws Exception {

    // Parse CommandLine Parameter
    Options options = new Options();
    options.addOption(
        Option.builder().longOpt("targetdir").hasArg()
            .desc("Target Directory Path").build());
    options.addOption(
        Option.builder().longOpt("replace")
            .desc("Replace Markdown file").build());
    options.addOption(
        Option.builder().longOpt("clear")
            .desc("Clear Result of Markdown file").build());
    options.addOption(
        Option.builder().longOpt("files").argName("file(s)").hasArgs()
            .desc("Markdown files").build());
    options.addOption(
        Option.builder().longOpt("help")
            .desc("Print usage").build());
    CommandLine cmd = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cmd = parser.parse(options, args);
    } catch (UnrecognizedOptionException e) {
      System.out.println("Unrecognized option: " + e.getOption()); // NOPMD
      System.exit(1);
    }

    // print usage
    if (cmd.hasOption("help")) {
      HelpFormatter f = new HelpFormatter();
      f.printHelp("sparql-markdown [options]", options);
      return;
    }

    // load model
    String targetdir = ".";
    if (cmd.hasOption("targetdir")) {
      targetdir = cmd.getOptionValue("targetdir");
    }
    Model model = createModel(
        Paths.get(targetdir).toFile().getCanonicalPath());

    // process markdown
    List<String> files = new ArrayList<>();
    if (cmd.hasOption("files")) {
      files = Arrays.stream(cmd.getOptionValues("files")).toList();
    }
    boolean replace = cmd.hasOption("replace");
    boolean clear = cmd.hasOption("clear");
    files.forEach(f -> {
      try {
        String target = Paths.get(f).toFile().getCanonicalPath();
        processMarkdown(target, model, replace, clear);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public static Model createModel(String parent) throws IOException {
    // load rdf
    File targetDir = Paths.get(parent).toFile();
    String parentPath = targetDir.getCanonicalPath();

    Graph g = Factory.createGraphMem();
    Files.walk(Paths.get(parentPath))
        .filter(e -> e.toString().endsWith(".rdf") || e.toString().endsWith(".ttl"))
        .forEach(e -> {
          Graph gf = Factory.createGraphMem();
          RDFParser.source(e.toString()).parse(gf);
          List<Triple> lst = gf.find().toList();
          gf.close();
          lst.forEach(g::add);

          gf.getPrefixMapping().getNsPrefixMap()
              .forEach((k, v) -> g.getPrefixMapping().setNsPrefix(k, v));
        });
    return ModelFactory.createModelForGraph(g);
  }

  public static void processMarkdown(String mdFile, Model model, boolean replace, boolean clear)
      throws IOException {
    Path file = Paths.get(mdFile);
    List<String> text = Files.readAllLines(file);
    Iterator<String> it = text.iterator();
    List<String> processed = new ArrayList<>();
    boolean afterResult = false;
    while (it.hasNext()) {
      String ln = it.next();
      if (RESULT_BEGIN.matcher(ln).matches()) {
        while (it.hasNext()) {
          ln = it.next();
          if (RESULT_END.matcher(ln).matches()) {
            break;
          }
        }
      } else {
        if (afterResult) {
          if (ln.equals("")) {
            continue;
          } else {
            afterResult = false;
          }
        }
        processed.add(ln);
      }
      if (SPARQL_BEGIN.matcher(ln).matches()) {
        StringBuilder buff = new StringBuilder();
        while (it.hasNext()) {
          ln = it.next();
          processed.add(ln);
          if (SPARQL_END.matcher(ln).matches()) {
            break;
          }
          buff.append(ln).append("\n");
        }
        if (!clear) {
          processed.add("");
          processed.add(RESULT_BEGIN_COMMENT);
          processed.add("");
          processed.addAll(queryMarkdown(model, buff.toString()));
          processed.add("");
          processed.add(RESULT_END_COMMENT);
        }
        processed.add("");
        afterResult = true;
      }
    }
    if (replace) {
      Files.write(file, processed, StandardCharsets.UTF_8,
          StandardOpenOption.TRUNCATE_EXISTING);
    } else {
      System.out.println("-------- " + mdFile);
      System.out.println(String.join("\n", processed));
      System.out.println("");
    }
  }

  public static List<String> queryMarkdown(Model model, String queryString) {
    List<String> lines = new ArrayList<>();
    try {
      Query query = QueryFactory.create(queryString);
      QueryExecution qe = QueryExecutionFactory.create(query, model);
      Map<String, String> reversePrefixMap = new HashMap<>();
      model.getNsPrefixMap().forEach((k, v) -> reversePrefixMap.put(v, k));

      ResultSet results = qe.execSelect();
      List<String> header = results.getResultVars();
      lines.add("| " + String.join(" | ", header) + " |");
      lines.add("|" + StringUtils.repeat(" ------------- |", header.size()));
      while (results.hasNext()) {
        QuerySolution sol = results.next();
        List<String> row = new ArrayList<>();
        for (String h : header) {
          RDFNode v = sol.get(h);
          if (v == null) {
            row.add("");
          } else if (v.isLiteral()) {
            row.add(sol.getLiteral(h).toString());
          } else if (v.isResource()) {
            String resouceString = sol.getResource(h).toString();
            for (Map.Entry<String, String> e : reversePrefixMap.entrySet()) {
              if (resouceString.startsWith(e.getKey())) {
                resouceString = resouceString.replaceFirst(e.getKey(), e.getValue() + ":");
              }
            }
            row.add(resouceString);
          } else {
            row.add(v.toString());
          }
        }
        lines.add("| " + String.join(" | ", row) + " |");
      }
    } catch (Exception ex) {
      lines.add("```");
      lines.add("[Error: " + ex.getClass().getName() + "]");
      lines.add("");
      lines.addAll(Arrays.stream(ex.getMessage().split("\n")).toList());
      lines.add("```");
    }
    return lines;
  }

}
