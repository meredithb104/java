package com.structurizr.componentfinder.javadoc;

import com.structurizr.Workspace;
import com.structurizr.componentfinder.ComponentFinder;
import com.structurizr.componentfinder.JavadocComponentFinderStrategy;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Model;
import com.structurizr.model.SoftwareSystem;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class JavadocComponentFinderStrategyTests {

    private Container webApplication;
    private Component componentA, componentB, componentC;
    private File sourcePath = new File("test/unit");

    @Before
    public void setUp() {
        Workspace workspace = new Workspace("Name", "Description");
        Model model = workspace.getModel();

        SoftwareSystem softwareSystem = model.addSoftwareSystem("Name", "Description");
        webApplication = softwareSystem.addContainer("Name", "Description", "Technology");

        componentA = webApplication.addComponentOfType(
                "com.structurizr.componentfinder.javadoc.componentA.ComponentA",
                "com.structurizr.componentfinder.javadoc.componentA.ComponentAImpl",
                "", "");

        componentB = webApplication.addComponentOfType(
                "com.structurizr.componentfinder.javadoc.componentB.ComponentB",
                "com.structurizr.componentfinder.javadoc.componentB.ComponentBImpl",
                "", "");

        componentC = webApplication.addComponentOfType(
                "com.structurizr.componentfinder.javadoc.componentC.ComponentC",
                "com.structurizr.componentfinder.javadoc.componentC.ComponentCImpl",
                "", "");
    }

    @Test
    public void test_findComponents() throws Exception {
        ComponentFinder componentFinder = new ComponentFinder(
                webApplication,
                "com.structurizr.componentfinder.javadoc",
                new JavadocComponentFinderStrategy(sourcePath)
        );
        componentFinder.findComponents();

        assertEquals("A component that does something.", componentA.getDescription());
        assertEquals("A component that does something else.", componentB.getDescription());
        assertEquals("A component that does something else too.", componentC.getDescription());
    }

    @Test
    public void test_findComponents_TruncatesComponentDescriptions_WhenComponentDescriptionsAreTooLong() throws Exception {
        ComponentFinder componentFinder = new ComponentFinder(
                webApplication,
                "com.structurizr.componentfinder.javadoc",
                new JavadocComponentFinderStrategy(sourcePath, 32)
        );
        componentFinder.findComponents();

        assertEquals("A component that does something.", componentA.getDescription());
        assertEquals("A component that does somethi...", componentB.getDescription());
        assertEquals("A component that does somethi...", componentC.getDescription());
    }

}
