/**
 * eobjects.org MetaModel
 * Copyright (C) 2010 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.metamodel.data;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eobjects.metamodel.MetaModelException;

/**
 * Row publisher implementation used by {@link RowPublisherDataSet}.
 * 
 * @author Kasper Sørensen
 */
class RowPublisherImpl implements RowPublisher {

	public static final int BUFFER_SIZE = 20;

	private final RowPublisherDataSet _dataSet;
	private final BlockingQueue<Row> _queue;
	private final AtomicBoolean _finished;
	private final AtomicInteger _rowCount;
	private volatile Row _currentRow;
	private volatile Exception _error;

	public RowPublisherImpl(RowPublisherDataSet dataSet) {
		_dataSet = dataSet;
		_queue = new ArrayBlockingQueue<Row>(BUFFER_SIZE);
		_finished = new AtomicBoolean(false);
		_rowCount = new AtomicInteger();
	}

	@Override
	public boolean publish(Row row) {
		if (_finished.get()) {
			return false;
		}
		while (!offer(row)) {
			if (_finished.get()) {
				return false;
			}
			// wait one more cycle
		}
		int rowCount = _rowCount.incrementAndGet();
		if (_dataSet.getMaxRows() > 0 && rowCount >= _dataSet.getMaxRows()) {
			finished();
			return false;
		}
		return true;
	}

	private boolean offer(Row row) {
		try {
			return _queue.offer(row, 1000, TimeUnit.MICROSECONDS);
		} catch (InterruptedException e) {
			// do nothing
			return false;
		}
	}

	@Override
	public boolean publish(Object[] values) {
		Row row = new DefaultRow(_dataSet.getHeader(), values);
		return publish(row);
	}

	@Override
	public boolean publish(Object[] values, Style[] styles) {
		Row row = new DefaultRow(_dataSet.getHeader(), values, styles);
		return publish(row);
	}

	@Override
	public void finished() {
		_finished.set(true);
	}

	public boolean next() {
		if (_queue.isEmpty() && _finished.get()) {
			return false;
		}
		try {
			_currentRow = _queue.poll(1000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// do nothing
		}
		if (_currentRow != null) {
			return true;
		}
		if (_error != null) {
			if (_error instanceof RuntimeException) {
				throw (RuntimeException) _error;
			}
			throw new MetaModelException(_error);
		}
		// "busy" (1 second) wait
		return next();
	}

	public Row getRow() {
		return _currentRow;
	}

	public void failed(Exception error) {
		_error = error;
	}
}