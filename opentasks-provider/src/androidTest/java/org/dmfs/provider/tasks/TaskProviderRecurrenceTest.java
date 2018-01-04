/*
 * Copyright 2018 dmfs GmbH
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

package org.dmfs.provider.tasks;

import android.content.ContentProviderClient;
import android.content.Context;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.dmfs.android.contentpal.Operation;
import org.dmfs.android.contentpal.OperationsQueue;
import org.dmfs.android.contentpal.RowSnapshot;
import org.dmfs.android.contentpal.Table;
import org.dmfs.android.contentpal.operations.Assert;
import org.dmfs.android.contentpal.operations.BulkDelete;
import org.dmfs.android.contentpal.operations.Counted;
import org.dmfs.android.contentpal.operations.Put;
import org.dmfs.android.contentpal.predicates.AllOf;
import org.dmfs.android.contentpal.predicates.EqArg;
import org.dmfs.android.contentpal.predicates.ReferringTo;
import org.dmfs.android.contentpal.queues.BasicOperationsQueue;
import org.dmfs.android.contentpal.rowdata.CharSequenceRowData;
import org.dmfs.android.contentpal.rowdata.Composite;
import org.dmfs.android.contentpal.rowdata.EmptyRowData;
import org.dmfs.android.contentpal.rowsnapshots.VirtualRowSnapshot;
import org.dmfs.android.contenttestpal.operations.AssertEmptyTable;
import org.dmfs.android.contenttestpal.operations.AssertRelated;
import org.dmfs.iterables.SingletonIterable;
import org.dmfs.iterables.elementary.Seq;
import org.dmfs.opentaskspal.tables.InstanceTable;
import org.dmfs.opentaskspal.tables.LocalTaskListsTable;
import org.dmfs.opentaskspal.tables.TaskListScoped;
import org.dmfs.opentaskspal.tables.TaskListsTable;
import org.dmfs.opentaskspal.tables.TasksTable;
import org.dmfs.opentaskspal.tasks.DueData;
import org.dmfs.opentaskspal.tasks.ExDatesTaskData;
import org.dmfs.opentaskspal.tasks.InstanceData;
import org.dmfs.opentaskspal.tasks.OriginalInstanceData;
import org.dmfs.opentaskspal.tasks.RDatesTaskData;
import org.dmfs.opentaskspal.tasks.RRuleTaskData;
import org.dmfs.opentaskspal.tasks.TimeData;
import org.dmfs.opentaskspal.tasks.TitleData;
import org.dmfs.optional.Present;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Duration;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.tasks.contract.TaskContract.Instances;
import org.dmfs.tasks.contract.TaskContract.TaskLists;
import org.dmfs.tasks.contract.TaskContract.Tasks;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.TimeZone;

import static org.dmfs.android.contenttestpal.ContentMatcher.resultsIn;
import static org.dmfs.optional.Absent.absent;
import static org.junit.Assert.assertThat;


/**
 * Recurrence Tests for {@link TaskProvider}.
 *
 * @author Marten Gajda
 */
@RunWith(AndroidJUnit4.class)
public class TaskProviderRecurrenceTest
{
    private String mAuthority;
    private Context mContext;
    private ContentProviderClient mClient;


    @Before
    public void setUp() throws Exception
    {
        mContext = InstrumentationRegistry.getTargetContext();
        mAuthority = AuthorityUtil.taskAuthority(mContext);
        mClient = mContext.getContentResolver().acquireContentProviderClient(mAuthority);

        // Assert that tables are empty:
        OperationsQueue queue = new BasicOperationsQueue(mClient);
        queue.enqueue(new Seq<Operation<?>>(
                new AssertEmptyTable<>(new TasksTable(mAuthority)),
                new AssertEmptyTable<>(new TaskListsTable(mAuthority)),
                new AssertEmptyTable<>(new InstanceTable(mAuthority))));
        queue.flush();
    }


    @After
    public void tearDown() throws Exception
    {
        /*
        TODO When Test Orchestration is available, there will be no need for clean up here and check in setUp(), every test method will run in separate instrumentation
        https://android-developers.googleblog.com/2017/07/android-testing-support-library-10-is.html
        https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator
        */

        // Clear the DB:
        BasicOperationsQueue queue = new BasicOperationsQueue(mClient);
        queue.enqueue(new SingletonIterable<Operation<?>>(new BulkDelete<>(new LocalTaskListsTable(mAuthority))));
        queue.flush();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        {
            mClient.close();
        }
        else
        {
            mClient.release();
        }
    }


    /**
     * Test if instances of a task with a DTSTART, DUE and an RRULE.
     */
    @Test
    public void testRRule() throws InvalidRecurrenceRuleException
    {
        RowSnapshot<TaskLists> taskList = new VirtualRowSnapshot<>(new LocalTaskListsTable(mAuthority));
        Table<Instances> instancesTable = new InstanceTable(mAuthority);
        RowSnapshot<Tasks> task = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));

        Duration hour = new Duration(1, 0, 3600 /* 1 hour */);
        DateTime start = DateTime.parse("20180104T123456Z");
        DateTime due = start.addDuration(hour);
        DateTime localStart = start.shiftTimeZone(TimeZone.getDefault());

        Duration day = new Duration(1, 1, 0);

        DateTime second = localStart.addDuration(day);
        DateTime third = second.addDuration(day);
        DateTime fourth = third.addDuration(day);
        DateTime fifth = fourth.addDuration(day);

        DateTime localDue = due.shiftTimeZone(TimeZone.getDefault());

        assertThat(new Seq<>(
                        new Put<>(taskList, new EmptyRowData<>()),
                        new Put<>(task,
                                new Composite<>(
                                        new TimeData(start, due),
                                        new RRuleTaskData(new RecurrenceRule("FREQ=DAILY;COUNT=5", RecurrenceRule.RfcMode.RFC2445_LAX))))

                ), resultsIn(mClient,
                new Assert<>(task,
                        new Composite<>(
                                new TimeData(start, due),
                                new CharSequenceRowData<>(Tasks.RRULE, "FREQ=DAILY;COUNT=5"))),
                new Counted<>(5, new AssertRelated<>(instancesTable, Instances.TASK_ID, task)),
                // 1st instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(localStart, localDue, new Present<>(start), 0),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, start.getTimestamp())),
                // 2nd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(second, second.addDuration(hour), new Present<>(second), 0 /*TODO: this should be 1 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, second.getTimestamp())),
                // 3rd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(third, third.addDuration(hour), new Present<>(third), 0 /*TODO: this should be 2 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, third.getTimestamp())),
                // 4th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fourth, fourth.addDuration(hour), new Present<>(fourth), 0 /*TODO: this should be 3 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fourth.getTimestamp())),
                // 5th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fifth, fifth.addDuration(hour), new Present<>(fifth), 0 /*TODO: this should be 4 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fifth.getTimestamp())))
        );
    }


    /**
     * Test if instances of a task with a DUE and an RRULE but no DTSTART.
     */
    @Test
    public void testRRuleNoDtStart() throws InvalidRecurrenceRuleException
    {
        RowSnapshot<TaskLists> taskList = new VirtualRowSnapshot<>(new LocalTaskListsTable(mAuthority));
        Table<Instances> instancesTable = new InstanceTable(mAuthority);
        RowSnapshot<Tasks> task = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));

        Duration hour = new Duration(1, 0, 3600 /* 1 hour */);
        DateTime due = DateTime.parse("20180104T123456Z");

        Duration day = new Duration(1, 1, 0);

        DateTime localDue = due.shiftTimeZone(TimeZone.getDefault());

        DateTime second = localDue.addDuration(day);
        DateTime third = second.addDuration(day);
        DateTime fourth = third.addDuration(day);
        DateTime fifth = fourth.addDuration(day);

        assertThat(new Seq<>(
                        new Put<>(taskList, new EmptyRowData<>()),
                        new Put<>(task,
                                new Composite<>(
                                        new DueData(due),
                                        new RRuleTaskData(new RecurrenceRule("FREQ=DAILY;COUNT=5", RecurrenceRule.RfcMode.RFC2445_LAX))))

                ), resultsIn(mClient,
                new Assert<>(task,
                        new Composite<>(
                                new DueData(due),
                                new CharSequenceRowData<>(Tasks.RRULE, "FREQ=DAILY;COUNT=5"))),
                new Counted<>(5, new AssertRelated<>(instancesTable, Instances.TASK_ID, task)),
                // 1st instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(absent(), new Present<>(localDue), new Present<>(due), 0),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, due.getTimestamp())),
                // 2nd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(absent(), new Present<>(second), new Present<>(second), 0 /*TODO: this should be 1 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, second.getTimestamp())),
                // 3rd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(absent(), new Present<>(third), new Present<>(third), 0 /*TODO: this should be 2 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, third.getTimestamp())),
                // 4th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(absent(), new Present<>(fourth), new Present<>(fourth), 0 /*TODO: this should be 3 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fourth.getTimestamp())),
                // 5th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(absent(), new Present<>(fifth), new Present<>(fifth), 0 /*TODO: this should be 4 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fifth.getTimestamp())))
        );
    }


    /**
     * Test if instances of a task with a DTSTART and an RRULE but no DUE
     */
    @Test
    public void testRRuleNoDue() throws InvalidRecurrenceRuleException
    {
        RowSnapshot<TaskLists> taskList = new VirtualRowSnapshot<>(new LocalTaskListsTable(mAuthority));
        Table<Instances> instancesTable = new InstanceTable(mAuthority);
        RowSnapshot<Tasks> task = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));

        Duration hour = new Duration(1, 0, 3600 /* 1 hour */);
        DateTime start = DateTime.parse("20180104T123456Z");

        Duration day = new Duration(1, 1, 0);

        DateTime localStart = start.shiftTimeZone(TimeZone.getDefault());

        DateTime second = localStart.addDuration(day);
        DateTime third = second.addDuration(day);
        DateTime fourth = third.addDuration(day);
        DateTime fifth = fourth.addDuration(day);

        assertThat(new Seq<>(
                        new Put<>(taskList, new EmptyRowData<>()),
                        new Put<>(task,
                                new Composite<>(
                                        new TimeData(start),
                                        new RRuleTaskData(new RecurrenceRule("FREQ=DAILY;COUNT=5", RecurrenceRule.RfcMode.RFC2445_LAX))))

                ), resultsIn(mClient,
                new Assert<>(task,
                        new Composite<>(
                                new TimeData(start),
                                new CharSequenceRowData<>(Tasks.RRULE, "FREQ=DAILY;COUNT=5"))),
                new Counted<>(5, new AssertRelated<>(instancesTable, Instances.TASK_ID, task)),
                // 1st instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(new Present<>(localStart), absent(), new Present<>(start), 0),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, start.getTimestamp())),
                // 2nd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(new Present<>(second), absent(), new Present<>(second), 0 /*TODO: this should be 1 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, second.getTimestamp())),
                // 3rd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(new Present<>(third), absent(), new Present<>(third), 0 /*TODO: this should be 2 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, third.getTimestamp())),
                // 4th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(new Present<>(fourth), absent(), new Present<>(fourth), 0 /*TODO: this should be 3 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fourth.getTimestamp())),
                // 5th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(new Present<>(fifth), absent(), new Present<>(fifth), 0 /*TODO: this should be 4 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fifth.getTimestamp())))
        );
    }


    /**
     * Remove an instance from a task with an RRULE.
     */
    @Test
    public void testRRuleRemoveInstance() throws InvalidRecurrenceRuleException
    {
        RowSnapshot<TaskLists> taskList = new VirtualRowSnapshot<>(new LocalTaskListsTable(mAuthority));
        Table<Instances> instancesTable = new InstanceTable(mAuthority);
        RowSnapshot<Tasks> task = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));

        Duration hour = new Duration(1, 0, 3600 /* 1 hour */);
        DateTime start = DateTime.parse("20180104T123456Z");
        DateTime due = start.addDuration(hour);

        Duration day = new Duration(1, 1, 0);

        DateTime localStart = start.shiftTimeZone(TimeZone.getDefault());
        DateTime localDue = due.shiftTimeZone(TimeZone.getDefault());

        DateTime second = localStart.addDuration(day);
        DateTime third = second.addDuration(day);
        DateTime fourth = third.addDuration(day);
        DateTime fifth = fourth.addDuration(day);

        assertThat(new Seq<>(
                        new Put<>(taskList, new EmptyRowData<>()),
                        new Put<>(task,
                                new Composite<>(
                                        new TimeData(start, due),
                                        new RRuleTaskData(new RecurrenceRule("FREQ=DAILY;COUNT=5", RecurrenceRule.RfcMode.RFC2445_LAX)))),
                        // remove the third instance
                        new BulkDelete<>(instancesTable,
                                new AllOf(new ReferringTo<>(Instances.TASK_ID, task), new EqArg(Instances.INSTANCE_ORIGINAL_TIME, third.getTimestamp())))

                ), resultsIn(mClient,
                new Assert<>(task,
                        new Composite<>(
                                new TimeData(start, due),
                                new CharSequenceRowData<>(Tasks.RRULE, "FREQ=DAILY;COUNT=5"),
                                new CharSequenceRowData<>(Tasks.EXDATE, "20180106T123456Z"))),
                new Counted<>(5, new AssertRelated<>(instancesTable, Instances.TASK_ID, task)),
                // 1st instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(localStart, localDue, new Present<>(start), 0),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, start.getTimestamp())),
                // 2nd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(second, second.addDuration(hour), new Present<>(second), 0 /*TODO: this should be 1 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, second.getTimestamp())),
                // 4th instance (now 3rd):
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fourth, fourth.addDuration(hour), new Present<>(fourth), 0 /*TODO: this should be 2 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fourth.getTimestamp())),
                // 5th instance (now 4th):
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fifth, fifth.addDuration(hour), new Present<>(fifth), 0 /*TODO: this should be 3 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fifth.getTimestamp())))
        );
    }


    /**
     * Test RRULE with overridden instance (inserted into the tasks table)
     */
    @Test
    public void testRRuleWithOverride() throws InvalidRecurrenceRuleException
    {
        RowSnapshot<TaskLists> taskList = new VirtualRowSnapshot<>(new LocalTaskListsTable(mAuthority));
        Table<Instances> instancesTable = new InstanceTable(mAuthority);
        RowSnapshot<Tasks> task = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));
        RowSnapshot<Tasks> taskOverride = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));

        Duration hour = new Duration(1, 0, 3600 /* 1 hour */);
        DateTime start = DateTime.parse("20180104T123456Z");
        DateTime due = start.addDuration(hour);

        Duration day = new Duration(1, 1, 0);

        DateTime localStart = start.shiftTimeZone(TimeZone.getDefault());
        DateTime localDue = due.shiftTimeZone(TimeZone.getDefault());

        DateTime second = localStart.addDuration(day);
        DateTime third = second.addDuration(day);
        DateTime fourth = third.addDuration(day);
        DateTime fifth = fourth.addDuration(day);

        assertThat(new Seq<>(
                        new Put<>(taskList, new EmptyRowData<>()),
                        new Put<>(task,
                                new Composite<>(
                                        new TimeData(start, due),
                                        new TitleData("original"),
                                        new RRuleTaskData(new RecurrenceRule("FREQ=DAILY;COUNT=5", RecurrenceRule.RfcMode.RFC2445_LAX)))),
                        // the override moves the instance by an hour
                        new Put<>(taskOverride, new Composite<>(
                                new TimeData(third.addDuration(hour), third.addDuration(hour).addDuration(hour)),
                                new TitleData("override"),
                                new OriginalInstanceData(task, third)))
                ), resultsIn(mClient,
                new Assert<>(task,
                        new Composite<>(
                                new TimeData(start, due),
                                new CharSequenceRowData<>(Tasks.TITLE, "original"),
                                new CharSequenceRowData<>(Tasks.RRULE, "FREQ=DAILY;COUNT=5"))),
                new Assert<>(taskOverride,
                        new Composite<>(
                                new TimeData(third.addDuration(hour), third.addDuration(hour).addDuration(hour)),
                                new CharSequenceRowData<>(Tasks.TITLE, "override"),
                                new OriginalInstanceData(task, third))),
                new Counted<>(4, new AssertRelated<>(instancesTable, Instances.TASK_ID, task)),
                // 1st instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(localStart, localDue, new Present<>(start), 0),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, start.getTimestamp())),
                // 2nd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(second, second.addDuration(hour), new Present<>(second), 0 /*TODO: this should be 1 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, second.getTimestamp())),
                // 3th instance (the overridden one):
                new AssertRelated<>(instancesTable, Instances.TASK_ID, taskOverride,
                        new InstanceData(third.addDuration(hour), third.addDuration(hour).addDuration(hour), new Present<>(third),
                                0 /*TODO: this should be 3 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, third.getTimestamp())),
                // 4th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fourth, fourth.addDuration(hour), new Present<>(fourth), 0 /*TODO: this should be 3 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fourth.getTimestamp())),
                // 5th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fifth, fifth.addDuration(hour), new Present<>(fifth), 0 /*TODO: this should be 4 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fifth.getTimestamp())))
        );
    }


    /**
     * Test if instances of a task with a DTSTART, an RRULE and EXDATEs.
     */
    @Test
    public void testRRuleWithExDates() throws InvalidRecurrenceRuleException
    {
        RowSnapshot<TaskLists> taskList = new VirtualRowSnapshot<>(new LocalTaskListsTable(mAuthority));
        Table<Instances> instancesTable = new InstanceTable(mAuthority);
        RowSnapshot<Tasks> task = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));

        Duration hour = new Duration(1, 0, 3600 /* 1 hour */);
        DateTime start = DateTime.parse("20180104T123456Z");
        DateTime due = start.addDuration(hour);

        Duration day = new Duration(1, 1, 0);

        DateTime localStart = start.shiftTimeZone(TimeZone.getDefault());
        DateTime localDue = due.shiftTimeZone(TimeZone.getDefault());

        DateTime second = localStart.addDuration(day);
        DateTime third = second.addDuration(day);
        DateTime fourth = third.addDuration(day);
        DateTime fifth = fourth.addDuration(day);

        assertThat(new Seq<>(
                        new Put<>(taskList, new EmptyRowData<>()),
                        new Put<>(task,
                                new Composite<>(
                                        new TimeData(start, due),
                                        new RRuleTaskData(new RecurrenceRule("FREQ=DAILY;COUNT=5", RecurrenceRule.RfcMode.RFC2445_LAX)),
                                        new ExDatesTaskData(new Seq<>(third, fifth))))

                ), resultsIn(mClient,
                new Assert<>(task,
                        new Composite<>(
                                new TimeData(start, due),
                                new CharSequenceRowData<>(Tasks.RRULE, "FREQ=DAILY;COUNT=5"),
                                new CharSequenceRowData<>(Tasks.EXDATE, "20180106T123456Z,20180108T123456Z"))),
                new Counted<>(3, new AssertRelated<>(instancesTable, Instances.TASK_ID, task)),
                // 1st instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(localStart, localDue, new Present<>(start), 0),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, start.getTimestamp())),
                // 2nd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(second, second.addDuration(hour), new Present<>(second), 0 /*TODO: this should be 1 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, second.getTimestamp())),
                // 4th instance (now 3rd):
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fourth, fourth.addDuration(hour), new Present<>(fourth), 0 /*TODO: this should be 2 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fourth.getTimestamp())))
        );
    }


    /**
     * Test if instances of a task with a DTSTART and RDATEs.
     */
    @Test
    public void testRDate() throws InvalidRecurrenceRuleException
    {
        RowSnapshot<TaskLists> taskList = new VirtualRowSnapshot<>(new LocalTaskListsTable(mAuthority));
        Table<Instances> instancesTable = new InstanceTable(mAuthority);
        RowSnapshot<Tasks> task = new VirtualRowSnapshot<>(new TaskListScoped(taskList, new TasksTable(mAuthority)));

        Duration hour = new Duration(1, 0, 3600 /* 1 hour */);
        DateTime start = DateTime.parse("20180104T123456Z");
        DateTime due = start.addDuration(hour);

        Duration day = new Duration(1, 1, 0);

        DateTime localStart = start.shiftTimeZone(TimeZone.getDefault());
        DateTime localDue = due.shiftTimeZone(TimeZone.getDefault());

        DateTime second = localStart.addDuration(day);
        DateTime third = second.addDuration(day);
        DateTime fourth = third.addDuration(day);
        DateTime fifth = fourth.addDuration(day);

        assertThat(new Seq<>(
                        new Put<>(taskList, new EmptyRowData<>()),
                        new Put<>(task,
                                new Composite<>(
                                        new TimeData(start, due),
                                        new RDatesTaskData(new Seq<>(start, second, third, fourth, fifth))))

                ), resultsIn(mClient,
                new Assert<>(task,
                        new Composite<>(
                                new TimeData(start, due),
                                new CharSequenceRowData<>(Tasks.RDATE,
                                        "20180104T123456Z," +
                                                "20180105T123456Z," +
                                                "20180106T123456Z," +
                                                "20180107T123456Z," +
                                                "20180108T123456Z"
                                ))),
                new Counted<>(5, new AssertRelated<>(instancesTable, Instances.TASK_ID, task)),
                // 1st instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(localStart, localDue, new Present<>(start), 0),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, start.getTimestamp())),
                // 2nd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(second, second.addDuration(hour), new Present<>(second), 0 /*TODO: this should be 1 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, second.getTimestamp())),
                // 3rd instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(third, third.addDuration(hour), new Present<>(third), 0 /*TODO: this should be 2 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, third.getTimestamp())),
                // 4th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fourth, fourth.addDuration(hour), new Present<>(fourth), 0 /*TODO: this should be 3 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fourth.getTimestamp())),
                // 5th instance:
                new AssertRelated<>(instancesTable, Instances.TASK_ID, task,
                        new InstanceData(fifth, fifth.addDuration(hour), new Present<>(fifth), 0 /*TODO: this should be 4 */),
                        new EqArg(Instances.INSTANCE_ORIGINAL_TIME, fifth.getTimestamp())))
        );
    }
}
