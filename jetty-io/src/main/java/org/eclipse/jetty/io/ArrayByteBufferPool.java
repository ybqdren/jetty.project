//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.component.DumpableCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A ByteBuffer pool where ByteBuffers are held in queues that are held in array elements.</p>
 * <p>Given a capacity {@code factor} of 1024, the first array element holds a queue of ByteBuffers
 * each of capacity 1024, the second array element holds a queue of ByteBuffers each of capacity
 * 2048, and so on.</p>
 */
@ManagedObject
public class ArrayByteBufferPool extends AbstractByteBufferPool implements Dumpable
{
    private static final Logger LOG = LoggerFactory.getLogger(ArrayByteBufferPool.class);

    private final int _maxCapacity;
    private final int _minCapacity;
    /**
     * 存储直接内存的 ByteBuffer
     */
    private final ByteBufferPool.Bucket[] _direct;
    /**
     * 存储非直接内存的 ByteBuffer
     */
    private final ByteBufferPool.Bucket[] _indirect;
    private boolean _detailedDump = false;

    /**
     * Creates a new ArrayByteBufferPool with a default configuration.
     */
    public ArrayByteBufferPool()
    {
        this(-1, -1, -1);
    }

    /**
     * Creates a new ArrayByteBufferPool with the given configuration.
     *
     * @param minCapacity the minimum ByteBuffer capacity
     * @param factor the capacity factor
     * @param maxCapacity the maximum ByteBuffer capacity
     */
    public ArrayByteBufferPool(int minCapacity, int factor, int maxCapacity)
    {
        this(minCapacity, factor, maxCapacity, -1, 0, 0);
    }

    /**
     * Creates a new ArrayByteBufferPool with the given configuration.
     *
     * @param minCapacity the minimum ByteBuffer capacity
     * @param factor the capacity factor
     * @param maxCapacity the maximum ByteBuffer capacity
     * @param maxQueueLength the maximum ByteBuffer queue length
     */
    public ArrayByteBufferPool(int minCapacity, int factor, int maxCapacity, int maxQueueLength)
    {
        this(minCapacity, factor, maxCapacity, maxQueueLength, 0, 0);
    }

    /**
     * Creates a new ArrayByteBufferPool with the given configuration.
     *
     * @param minCapacity the minimum ByteBuffer capacity
     * @param factor the capacity factor
     * @param maxCapacity the maximum ByteBuffer capacity
     * @param maxQueueLength the maximum ByteBuffer queue length
     * @param maxHeapMemory the max heap memory in bytes, -1 for unlimited memory or 0 to use default heuristic.
     * @param maxDirectMemory the max direct memory in bytes, -1 for unlimited memory or 0 to use default heuristic.
     */
    public ArrayByteBufferPool(int minCapacity, int factor, int maxCapacity, int maxQueueLength, long maxHeapMemory, long maxDirectMemory)
    {
        super(factor, maxQueueLength, maxHeapMemory, maxDirectMemory);

        factor = getCapacityFactor();
        if (minCapacity <= 0)
            minCapacity = 0;
        if (maxCapacity <= 0)
            maxCapacity = 64 * 1024;
        if ((maxCapacity % factor) != 0 || factor >= maxCapacity)
            throw new IllegalArgumentException("The capacity factor must be a divisor of maxCapacity");
        _maxCapacity = maxCapacity;
        _minCapacity = minCapacity;

        // Initialize all buckets in constructor and never modify the array again.
        // ByteBuffer 的数量和 maxCapacity 有关
        int length = bucketFor(maxCapacity) + 1;
        _direct = new ByteBufferPool.Bucket[length];
        _indirect = new ByteBufferPool.Bucket[length];

        // 相同大小的 ByteBuffer 将会有一个 Bucket 管理，不同大小的 ByteBuffer 必然存放在不同的 ByteBuffer 中
        for (int i = 0; i < length; i++)
        {
            _direct[i] = newBucket(i, true);
            _indirect[i] = newBucket(i, false);
        }
    }

    @Override
    public ByteBuffer acquire(int size, boolean direct)
    {
        int capacity = size < _minCapacity ? size : capacityFor(bucketFor(size));
        // 在ByteBufferPool初始化时，并不会预先申请ByteBuffer填充到对象池中，而是在系统执行过程中对新建的ByteBuffer进行管理，
        // ByteBufferPool既是一个对象池，同时也是一个工厂
        // 根据所需要的 ByteBuffer 大小去查找一个最合适的 Bucket
        ByteBufferPool.Bucket bucket = bucketFor(size, direct);
        if (bucket == null)
            return newByteBuffer(capacity, direct);
        ByteBuffer buffer = bucket.acquire();
        if (buffer == null)
            return newByteBuffer(capacity, direct);
        return buffer;
    }

    // Byteuffer 使用完成需要归还对象池
    @Override
    public void release(ByteBuffer buffer)
    {
        if (buffer == null)
            return;

        int capacity = buffer.capacity();
        // Validate that this buffer is from this pool.
        if (capacity != capacityFor(bucketFor(capacity)))
        {
            if (LOG.isDebugEnabled())
                LOG.debug("ByteBuffer {} does not belong to this pool, discarding it", BufferUtil.toDetailString(buffer));
            return;
        }

        // Don't release into the pool if greater than the maximum ByteBuffer capacity.
        if (capacity > _maxCapacity)
            return;

        boolean direct = buffer.isDirect();
        // 找到对应的 Bucket
        ByteBufferPool.Bucket bucket = bucketFor(capacity, direct);
        // 如果找到Bucket则重置buffer，并且加入Bucket中。
        if (bucket != null)
        {
            bucket.release(buffer);
            releaseExcessMemory(direct, this::releaseMemory);
        }
        // 如果找不到则不进行归还
        // 很明显，当归还的ByteBuffer过大或者过小时，归还总是不成功的，在这种情况下ByteBufferPool将失去其对象池的复用功能。
    }

    private Bucket newBucket(int key, boolean direct)
    {
        return new Bucket(capacityFor(key), getMaxQueueLength(), updateMemory(direct));
    }

    @Override
    public void clear()
    {
        super.clear();
        for (int i = 0; i < _direct.length; ++i)
        {
            _direct[i].clear();
            _indirect[i].clear();
        }
    }

    protected void releaseMemory(boolean direct)
    {
        long oldest = Long.MAX_VALUE;
        int index = -1;
        Bucket[] buckets = bucketsFor(direct);
        for (int i = 0; i < buckets.length; ++i)
        {
            Bucket bucket = buckets[i];
            if (bucket.isEmpty())
                continue;
            long lastUpdate = bucket.getLastUpdate();
            if (lastUpdate < oldest)
            {
                oldest = lastUpdate;
                index = i;
            }
        }
        if (index >= 0)
        {
            Bucket bucket = buckets[index];
            bucket.clear();
        }
    }

    protected int bucketFor(int capacity)
    {
        return (int)Math.ceil((double)capacity / getCapacityFactor());
    }

    protected int capacityFor(int bucket)
    {
        return bucket * getCapacityFactor();
    }

    // 大于等于请求大小，并且最接近请求大小的一个Bucket。如果找到这么一个Bucket，并且Bucket中也确实存在空闲的ByteBuffer，
    // 那么直接返回ByteBuffer。如果找不到合适的Bucket（比如，请求的大小超过maxSize），或者Bucket中已经没有可用的ByteBuffer，
    // 则根据Bucket的大小（优先）或者请求大小创建一个ByteBuffer并返回
    private ByteBufferPool.Bucket bucketFor(int capacity, boolean direct)
    {
        if (capacity < _minCapacity)
            return null;
        int bucket = bucketFor(capacity);
        if (bucket >= _direct.length)
            return null;
        Bucket[] buckets = bucketsFor(direct);
        return buckets[bucket];
    }

    @ManagedAttribute("The number of pooled direct ByteBuffers")
    public long getDirectByteBufferCount()
    {
        return getByteBufferCount(true);
    }

    @ManagedAttribute("The number of pooled heap ByteBuffers")
    public long getHeapByteBufferCount()
    {
        return getByteBufferCount(false);
    }

    private long getByteBufferCount(boolean direct)
    {
        return Arrays.stream(bucketsFor(direct))
            .filter(Objects::nonNull)
            .mapToLong(Bucket::size)
            .sum();
    }

    // Package local for testing
    ByteBufferPool.Bucket[] bucketsFor(boolean direct)
    {
        return direct ? _direct : _indirect;
    }

    public boolean isDetailedDump()
    {
        return _detailedDump;
    }

    public void setDetailedDump(boolean detailedDump)
    {
        _detailedDump = detailedDump;
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        List<Object> dump = new ArrayList<>();
        dump.add(String.format("HeapMemory: %d/%d", getHeapMemory(), getMaxHeapMemory()));
        dump.add(String.format("DirectMemory: %d/%d", getDirectMemory(), getMaxDirectMemory()));

        List<Bucket> indirect = Arrays.stream(_indirect).filter(b -> !b.isEmpty()).collect(Collectors.toList());
        List<Bucket> direct = Arrays.stream(_direct).filter(b -> !b.isEmpty()).collect(Collectors.toList());
        if (isDetailedDump())
        {
            dump.add(new DumpableCollection("Indirect Buckets", indirect));
            dump.add(new DumpableCollection("Direct Buckets", direct));
        }
        else
        {
            dump.add("Indirect Buckets size=" + indirect.size());
            dump.add("Direct Buckets size=" + direct.size());
        }
        Dumpable.dumpObjects(out, indent, this, dump);
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{minBufferCapacity=%s, maxBufferCapacity=%s, maxQueueLength=%s, factor=%s}",
            this.getClass().getSimpleName(), hashCode(),
            _minCapacity,
            _maxCapacity,
            getMaxQueueLength(),
            getCapacityFactor());
    }
}
