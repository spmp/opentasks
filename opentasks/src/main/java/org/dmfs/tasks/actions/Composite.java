/*
 * Copyright 2019 dmfs GmbH
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

package org.dmfs.tasks.actions;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;

import org.dmfs.android.contentpal.RowDataSnapshot;
import org.dmfs.iterables.elementary.Seq;
import org.dmfs.tasks.contract.TaskContract;


/**
 * A {@link TaskAction} which is composed of other {@link TaskAction}s.
 *
 * @author Marten Gajda
 */
public final class Composite implements TaskAction
{
    private final Iterable<TaskAction> mDelegates;


    public Composite(TaskAction... delegates)
    {
        this(new Seq<>(delegates));
    }


    public Composite(Iterable<TaskAction> delegates)
    {
        mDelegates = delegates;
    }


    @Override
    public void execute(Context context, ContentProviderClient contentProviderClient, RowDataSnapshot<TaskContract.Tasks> rowSnapshot, Uri taskUri) throws RemoteException, OperationApplicationException
    {
        for (TaskAction action : mDelegates)
        {
            action.execute(context, contentProviderClient, rowSnapshot, taskUri);
        }
    }
}
