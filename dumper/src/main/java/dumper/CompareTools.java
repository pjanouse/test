/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dumper;

import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import static dumper.CompareResultType.CONTAINS;
import static dumper.CompareResultType.CONTAINS_ALIAS;
import static dumper.CompareResultType.NOTCONTAINS;
import static dumper.CompareResultType.NOTIMPORTANT;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.codehaus.jackson.JsonNode;

/**
 *
 * @author mstyk
 */
public class CompareTools {

    private static final String NODE1_NAME = "EAP6";
    private static final String NODE2_NAME = "EAP7";
    private static final String NODE1_PATH = "subsystem=messaging";
    private static final String NODE2_PATH = "subsystem=messaging-activemq";

    private JsonNode rootEap6;
    private JsonNode rootEap7;
    private JsonNode nextNode;

    private boolean addWholeDifferentSubtree;

    private Translator translator;
    private List<String> notImportant;
    private SortedSetMultimap<String, String> result;
    private SortedSetMultimap<String, String> aliasResult;

    public CompareTools(JsonNode node1, JsonNode node2, boolean addWholeDifferentSubtree) {
        if (node1 == null || node2 == null) {
            throw new NullPointerException("node is null");
        }
        rootEap6 = node1;
        rootEap7 = node2;
        this.addWholeDifferentSubtree = addWholeDifferentSubtree;

        translator = new Translator();
        result = TreeMultimap.create();
        aliasResult = TreeMultimap.create();
        initNotImportantList();
    }

    public void compareNodes() {
        compareFields(rootEap6, rootEap7, NODE1_PATH, NODE2_PATH);
    }

    private void compareFields(JsonNode nodeEap6, JsonNode nodeEap7, String actualEap6Path, String actualEap7Path) {

        Iterator<Map.Entry<String, JsonNode>> fieldsEap6 = nodeEap6.getFields();
        Iterator<Map.Entry<String, JsonNode>> fieldsEap7 = nodeEap7.getFields();

        while (fieldsEap7.hasNext()) {
            Map.Entry<String, JsonNode> next = fieldsEap7.next();

            switch (isEntryKeyInIterator(next.getKey(), nodeEap6.getFields(), false)) {
                case NOTIMPORTANT:
                    break;
                case CONTAINS:
                    break;
                case NOTCONTAINS:
                    result.put(actualEap6Path, NODE2_NAME + " + " + next.getKey() + "\t\t [ " + actualEap7Path + " ] ");
                    if (addWholeDifferentSubtree) {
                        addSubtreeToResult(next.getValue(), actualEap6Path + translator.translateEap7toEap6(next.getKey()), actualEap7Path + translator.translateEap6toEap7(next.getKey()), false);
                    }
                    break;
                case CONTAINS_ALIAS:
                    aliasResult.put(actualEap6Path, NODE2_NAME + " + " + next.getKey() + "\t\t [ " + actualEap7Path + " ] ");
                    break;
            }
        }

        while (fieldsEap6.hasNext()) {
            nextNode = null;
            Map.Entry<String, JsonNode> next = fieldsEap6.next();

            switch (isEntryKeyInIterator(next.getKey(), nodeEap7.getFields(), true)) {
                case NOTIMPORTANT:
                    break;
                case CONTAINS:
                    if (nextNode != null) {
                        compareFields(next.getValue(), nextNode, actualEap6Path + "/" + next.getKey(), actualEap7Path + "/" + translator.translateEap6toEap7(next.getKey()));
                    }
                    break;
                case NOTCONTAINS:
                    result.put(actualEap6Path, NODE1_NAME + " + " + next.getKey() + "\t\t [ " + actualEap6Path + " ] ");
                    if (addWholeDifferentSubtree) {
                        addSubtreeToResult(next.getValue(), actualEap6Path, actualEap7Path, true);
                    }
                    break;
                case CONTAINS_ALIAS:
                    aliasResult.put(actualEap6Path, NODE1_NAME + " + " + next.getKey() + "\t\t [ " + actualEap6Path + " ] ");
                    if (nextNode != null) {
                        compareFields(next.getValue(), nextNode, actualEap6Path + "/" + next.getKey(), actualEap7Path + "/" + translator.translateEap6toEap7(next.getKey()));
                    }
                    break;
            }
        }
    }

    private CompareResultType isEntryKeyInIterator(String key, Iterator<Map.Entry<String, JsonNode>> fields, boolean isEap6Key) {

        boolean isEap7Key = !isEap6Key;

        if (notImportant.contains(key)) {
            nextNode = null;
            return NOTIMPORTANT;//true;
        }

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> next = fields.next();

            if (key.equalsIgnoreCase(next.getKey())) {
                nextNode = next.getValue();
                return CONTAINS;//true;
            } else {
                if (isEap6Key && translator.translateEap6toEap7(key).equalsIgnoreCase(next.getKey())) {
                    nextNode = next.getValue();
                    return CONTAINS_ALIAS;
                }
                if (isEap7Key && translator.translateEap7toEap6(key).equalsIgnoreCase(next.getKey())) {
                    nextNode = next.getValue();
                    return CONTAINS_ALIAS;
                }
            }
        }

        nextNode = null;
        return NOTCONTAINS;
    }

    private void addSubtreeToResult(JsonNode root, String actualEap6Path, String actualEap7Path, boolean isEap6) {
        List<String> dontAdd = Arrays.asList(new String[]{"children", "attributes", "operations", "*", "model-description"});

        Iterator<Map.Entry<String, JsonNode>> fields = root.getFields();

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> next = fields.next();
            if (!notImportant.contains(next.getKey())) {

                {
                    if (isEap6) {
                        if (!dontAdd.contains(next.getKey())) {
                            result.put(actualEap6Path, NODE1_NAME + " + " + next.getKey() + "\t\t [ " + actualEap6Path + " ] ");
                        }
                        addSubtreeToResult(next.getValue(), actualEap6Path + "/" + next.getKey(), actualEap7Path + "/" + translator.translateEap6toEap7(next.getKey()), true);

                    } else {
                        if (!dontAdd.contains(next.getKey())) {
                            result.put(actualEap6Path, NODE2_NAME + " + " + next.getKey() + "\t\t [ " + actualEap7Path + " ] ");
                        }
                        addSubtreeToResult(next.getValue(), actualEap6Path + "/" + translator.translateEap7toEap6(next.getKey()), actualEap7Path + "/" + next.getKey(), false);
                    }
                }
            }
        }
    }

    private void initNotImportantList() {
        String[] values = {
            "type",
            "description",
            "expressions-allowed",
            "nillable",
            "default",
            "access-type",
            "restart-required",
            "min-length",
            "max-length",
            "min",
            "max",
            "restart-required",
            "value-type",
            "required",
            "deprecated",
            "allowed",
            "unit",
            "attribute-group",
            "reply-properties",
            "restart-required",
            "alternatives",
            "storage",
            "request-properties",
            "operation-name",
            "access-constraints"
        };
        notImportant = Arrays.asList(values);
    }

    /*
     prints only difference in structure
     */
    public void printDifferenceResult() {
        Collection<Entry<String, String>> entries = result.entries();
        Entry<String, String> previous = null;

        System.out.println("---------------------------------------------------");
        System.out.println("RESULT (DIFFERENCES WITHOUT RENAMED FIELDS)");
        System.out.println("---------------------------------------------------\n\n");

        for (Entry<String, String> s : entries) {
            if (previous != null && previous.getKey() != s.getKey()) {
                System.out.println("");
            }
            System.out.println(s.getValue());
        }
    }

    public void printDifferenceResult(File f) throws IOException {
        Collection<Entry<String, String>> entries = result.entries();
        Entry<String, String> previous = null;
        PrintWriter writer = new PrintWriter(f);
        try {
            writer.println("---------------------------------------------------");
            writer.println("RESULT (DIFFERENCES WITHOUT RENAMED FIELDS)");
            writer.println("---------------------------------------------------\n\n");
            for (Entry<String, String> s : entries) {
                {
                    if (previous != null && previous.getKey() != s.getKey()) {
                        writer.println("");
                    }
                    writer.println(s.getValue());
                    previous = s;
                }
            }
        } finally {
            writer.close();
        }
        System.out.println("Diferences written to file " + f.getAbsolutePath());
    }

    /*
     prints only renamed fields
     */
    public void printAliasDifferenceResult() {
        Collection<Entry<String, String>> entries = aliasResult.entries();
        Entry<String, String> previous = null;

        System.out.println("---------------------------------------------------");
        System.out.println("RESULTS (ONLY RENAMED FIELDS)");
        System.out.println("---------------------------------------------------\n\n");

        for (Entry<String, String> s : entries) {
            if (previous != null && previous.getKey() != s.getKey()) {
                System.out.println("");
            }
            System.out.println(s.getValue());
        }
    }

    public void printAliasDifferenceResult(File f) throws IOException {
        Collection<Entry<String, String>> entries = aliasResult.entries();
        Entry<String, String> previous = null;
        PrintWriter writer = new PrintWriter(f);
        try {
            writer.println("---------------------------------------------------");
            writer.println("RESULTS (ONLY RENAMED FIELDS)");
            writer.println("---------------------------------------------------\n\n");
            for (Entry<String, String> s : entries) {
                {
                    if (previous != null && previous.getKey() != s.getKey()) {
                        writer.println("");
                    }
                    writer.println(s.getValue());
                    previous = s;
                }
            }
        } finally {
            writer.close();
        }
        System.out.println("Alias diferences written to file " + f.getAbsolutePath());
    }
    /*
     prints every difference
     */

    public void printResult() {
        SortedSetMultimap<String, String> totalResult = TreeMultimap.create(result);
        totalResult.putAll(aliasResult);
        Collection<Entry<String, String>> entries = totalResult.entries();

        Entry<String, String> previous = null;

        System.out.println("---------------------------------------------------");
        System.out.println("RESULTS (EVERY DIFFERENCE)");
        System.out.println("---------------------------------------------------\n\n");
        for (Entry<String, String> s : entries) {
            if (previous != null && previous.getKey() != s.getKey()) {
                System.out.println("");
            }
            System.out.println(s.getValue());
        }
    }

    public void printResult(File f) throws IOException {
        SortedSetMultimap<String, String> totalResult = TreeMultimap.create(result);
        totalResult.putAll(aliasResult);
        Collection<Entry<String, String>> entries = totalResult.entries();

        Entry<String, String> previous = null;
        PrintWriter writer = new PrintWriter(f);
        try {
            writer.println("---------------------------------------------------");
            writer.println("RESULTS (EVERY DIFFERENCE)");
            writer.println("---------------------------------------------------\n\n");
            for (Entry<String, String> s : entries) {
                {
                    if (previous != null && previous.getKey() != s.getKey()) {
                        writer.println("");
                    }
                    writer.println(s.getValue());
                    previous = s;
                }
            }
        } finally {
            writer.close();
        }
        System.out.println("All diferences written to file " + f.getAbsolutePath());
    }

}
