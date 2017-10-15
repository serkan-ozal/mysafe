# MySafe
My Unsafe - Unsafe Interceptor, Native Memory Leak Tracker and Access Checker on the JVM

## 1. What is MySafe?

**MySafe** is a framework (based on [Jillegal-Agent](https://github.com/serkan-ozal/jillegal-agent)) for managing memory accesses over `sun.misc.Unsafe`. **MySafe** intercepts (instruments) `sun.misc.Unsafe` calls and keeps records of allocated memories. So it can give the allocated memory informations and detect the invalid memory accesses.

## 2. Installation

In your `pom.xml`, you must add repository and dependency for **MySafe**. 
You can change `mysafe.version` to any existing **MySafe** library version.
Latest version of **MySafe** is `2.1`.

``` xml
...
<properties>
    ...
    <mysafe.version>2.1</mysafe.version>
    ...
</properties>
...
<dependencies>
    ...
	<dependency>
		<groupId>tr.com.serkanozal</groupId>
		<artifactId>mysafe</artifactId>
		<version>${mysafe.version}</version>
	</dependency>
	...
</dependencies>
...
<repositories>
	...
	<repository>
		<id>serkanozal-maven-repository</id>
		<url>https://github.com/serkan-ozal/maven-repository/raw/master/</url>
	</repository>
	...
</repositories>
...
```

## 3. Configurations

* **`mysafe.enableSafeMemoryManagementMode`:** Enables checkes while freeing/reallocating memory. By this property enabled, every memory free/reallocation are checked about if the target memory is valid (already allocated) or not. Default value is `false`.

* **`mysafe.enableSafeMemoryAccessMode`:** Enables memory access checkes over `sun.misc.Unsafe`. By this property enabled, every memory accesses over `sun.misc.Unsafe` are checked about if the target memory is valid (already allocated) or not. Default value is `false`.
 
* **`mysafe.enableConcurrentMemoryAccessCheck`:** Enables a very lightweight locking for every memory access/free operation. By this property enabled, when there is on going memory access, there cannot be memory free and when there is on going memory free, there cannot be memory access. However, when there is on going memory access, there can be other memory accesses and when there is on going memory free, there can be other memory frees. It means, memory accesses only lock memory frees and memory frees only lock memory accesses. This property can be used if there is no guarantee for that the accessed memory region can be free by other threads simultaneously. Note that this lock is not address based lock but global lock for memory access/free operation but it is very light weight and implemented by lock-free approaches with busy-spin based on the assumption that memory accesses/frees are very fast operations.

* **`mysafe.useCustomMemoryManagement`:** Enabled custom memory management mode. Custom memory management means that memory allocation/free/reallocation operations are not handled directly over `sun.misc.Unsafe` but over custom implementation. For example, user might acquire memory in batch from OS, caches it and then serves requested memories from there. In this mode, user can specify his/her custom memory allocation/free/reallocation points instead of `Unsafe::allocateMemory`/`Unsafe::freeMemory`/`Unsafe::reallocateMemory`. However, when this mode is enabled, **Safe Memory Access Mode** feature cannot be enabled at the same time. Custom memory management points can be configured via annotations (`@AllocationPoint`, `@FreePoint` and `@ReallocationPoint`) and properties file named `mysafe-config.properties`.

    - **Configuring custom memory management via annotation:** Custom memory management points can be configured by marking related methods with these annotations.
        * **`@AllocationPoint`:** Marks custom allocation points to be tracked. 
        
            Annotated method must be in the form of `long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)` as given parameter order by default. Order of `size` parameter can be configured via `sizeParameterOrder()`. 
            
            As you can see, 
            - There might be other parameters rather than `size`.
            - Return type can only be `long` and it must be allocated `address`.
        
            Also note that the marked method must be concrete method. Must not be neither method definition on interface nor on abstract class.
            
        * **`@FreePoint`:** Marks custom free points to be tracked.

            Annotated method must be in the form of `void $YOUR_FREE_METHOD_NAME$(long address, ...)` as given parameter order by default. Order of `address` parameter can be configured via `addressParameterOrder()`. 
            
            As you can see, 
            - There might be other parameters rather than `address`.
            - Return type can only be `void`.
            
            Also note that the marked method must be concrete method. Must not be neither method definition on interface nor on abstract class.
            
        * **`@ReallocationPoint`:** Marks custom reallocation points to be tracked.

            Annotated method must be in the form of `long $YOUR_REALLOCATION_METHOD_NAME$(long oldAddress, long newSize, ...)` as given parameter order by default. Order of `oldAddress` and `newSize` parameters can be configured via `oldAddressParameterOrder()` and `newSizeParameterOrder()`.

            As you can see, 
            - There might be other parameters rather than `oldAddress` and `newSize`.
            - Return type can only be `long` and it must be reallocated `address`.

            Also note that the marked method must be concrete method. Must not be neither method definition on interface nor on abstract class.
    
    - **Configuring custom memory management via properties file:** In the `mysafe-config.properties` file, memory management management point configurations are represented by properties. Key of property represents the memory management management point configuration and value of property represents the management management point type. Memory management point types are `ALLOCATION_POINT`, `FREE_POINT` and `REALLOCATION_POINT`. 
        Here are the  memory management management point configuration syntaxes:
        * **`ALLOCATION_POINT`:** It must be in the form of `<class_name>#<method_name>(#<size_parameter_order>)?`
        * **`FREE_POINT`:** It must be in the form of `<class_name>#<method_name>(#<address_parameter_order>)?`
        * **`REALLOCATION_POINT`:** It must be in the form of `<class_name>#<method_name>(#<old_address_parameter_order>(#<new_size_parameter_order>)?)?`
        
        Here is sample custom memory management config via `mysafe-config.properties`:
        ```
        tr.com.serkanozal.mysafe.CustomMemoryManagementDemo$MemoryManager#allocate=ALLOCATION_POINT
        tr.com.serkanozal.mysafe.CustomMemoryManagementDemo$MemoryManager#free=FREE_POINT
        tr.com.serkanozal.mysafe.CustomMemoryManagementDemo$MemoryManager#reallocate=REALLOCATION_POINT
        ```

* **`mysafe.customMemoryManagementPackagePrefix`:** Specifies a subset of classes/packages for checking loaded classes whether they might have custom memory management point. By this configuration, unnecessary check on every loaded classes is prevented for possible custom memory management points.

* **`mysafe.threadLocalMemoryUsagePatternExist`:** Enables thread-local based storages for allocated memories and allocation paths. Since storages are thread-local, they are lock free and no need to any synchronization. By these advantages, they perform better than lock guarded and synchonized global storages. If memory usages are thread-local in your application, it is highly recommended to enable this property. Thread-local memory usage means that once a memory is allocated in a thread, it is only accessed and free within that thread.

* **`mysafe.ignoreByMySafe`:** Specifies classes/packages to be ignored by **MySafe** for instrumentation. There can be multiple configurations seperated by comma (`,`). Also via `@IgnoreByMySafe` annotation, classes can be marked to be ignored by **MySafe**.

* **`mysafe.threadLocalMemoryUsageDeciderImpl`:** Specifies the `ThreadLocalMemoryUsageDecider` implementation to be used for deciding which threads use memory as thread-local and which ones use as global. This property is used when `mysafe.threadLocalMemoryUsagePatternExist` property is enabled. By default all threads are assumed as they are using memory as thread-local when `mysafe.threadLocalMemoryUsagePatternExist` property is enabled.

* **`mysafe.enableAllocationPathMonitoringMode`:** Enables tracking allocation paths on memory allocation (class name and method name) with at most `4` depth by default. Allocation paths are dumped while dumping all allocated memories through `MySafe::dumpAllocatedMemories` if it is enabled. Default value is `false`. 

  Note that, in the result allocation path, between two subsequent call points (methods), there might be other call points (methods) and **MySafe** __doesn't give any guarantee__ that these call points (methods) are directly connected with eachother.

* **`mysafe.maxAllocationPathDepth`:** Configures maximum depth of for allocation path tracking. Default value is `4` and it __cannot__ be more than `4`.

* **`mysafe.enableMXBean`:** Enables JMX support. Default value is `false`.

* **`mysafe.allocatedMemoryStorageImpl`:** Specifies the custom `AllocatedMemoryStorage` implementation which stores the allocated memories. If it is not set, the default (built-in) `AllocatedMemoryStorage` implementation is used.

* **`mysafe.allocationPathManagerImpl`** Specifies the custom `AllocationPathManager` implementation which manages allocation path & address mappings/un-mappings and allocation path provide related operations. If it is not set, the default (built-in) `AllocationPathManager` implementation (`InstrumentationBasedAllocationPathManager`) is used.

* **`mysafe.illegalMemoryAccessListenerImpl`:** Specifies the `IllegalMemoryAccessListener` implementation to be notified when illegal memory access occurred.

* **`mysafe.useNativeMemoryForStorageWhenSupported`:** Enables usage of native memory (off-heap) backed storages when supported (only supported by thread-local storage at the moment).

## 4. Usage

There are 3 ways of activating **MySafe**:

### 4.1. Java Agent Based Usage by VM Argument 

**MySafe** can be activated through Java agent (**Jillegal-Agent**) by using `sun.misc.Unsafe` instrumenter of **MySafe** via `-javaagent:<path_to_jillegal_agent>\<jillegal_agent_jar>="-p tr.com.serkanozal.mysafe.impl.processor.MySafeProcessor"`.

For example: `-javaagent:$M2_HOME\tr\com\serkanozal\jillegal-agent\2.0\jillegal-agent-2.0.jar="-p tr.com.serkanozal.mysafe.impl.processor.MySafeProcessor"`

### 4.2. Java Agent Based Usage Programmatically

**MySafe** can be activated programmatically by `MySafe.youAreMine();`.

### 4.3. ClassLoader Based Usage by VM Argument 

**MySafe** can be activated also by defining its classloader as system classloader via  `-Djava.system.class.loader=tr.com.serkanozal.mysafe.impl.classloader.MySafeClassLoader`.

## 5. API

### 5.1. AllocatedMemoryStorage 

`AllocatedMemoryStorage` interface is contract point to store allocated memories. It is specified via `mysafe.allocatedMemoryStorageImpl` system property.

### 5.2. IllegalMemoryAccessListener 

`IllegalMemoryAccessListener` interface is contract point to be notified when illegal memory access occurred. It is specified via `mysafe.illegalMemoryAccessListenerImpl` system property. 

### 5.3. AllocatedMemoryIterator 

`AllocatedMemoryIterator` interface is contract point for iterating allocated memories.

Here is its sample usage:
``` java
// Iterate on all allocated memories and print them
MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {

  @Override
  public void onAllocatedMemory(long address, long size) {
    System.out.println("onAllocatedMemory >>> " + 
                          "address=" + address + 
                          ", size=" + size);
  }
                
});
```

### 5.4. MemoryListener 

`MemoryListener` interface is contract point to be notified for memory usage (allocation/free/reallocation).

Here is its sample usage:
``` java
// Create listener to be notified for each allocate/free/reallocate
MemoryListener listener = new MemoryListener() {

  @Override
  public void beforeAllocateMemory(long size) {
    System.out.println("beforeAllocateMemory >>> " + 
                          "size=" + size);
  }
                
  @Override
  public void afterAllocateMemory(long address, long size) {
    System.out.println("afterAllocateMemory >>> " + 
                          "address=" + address + 
                          ", size=" + size);
  }
                
  @Override
  public void beforeFreeMemory(long address) {
    System.out.println("beforeFreeMemory >>> " + 
                          "address=" + address);
  }
                
  @Override
  public void afterFreeMemory(long address, long size, boolean isKnownAddress) {
    System.out.println("afterFreeMemory >>> " + 
                          "address=" + address + 
                          ", size=" + size + 
                          ", isKnownAddress=" + isKnownAddress);
  }

  @Override
  public void beforeReallocateMemory(long oldAddress, long oldSize) {
    System.out.println("beforeReallocateMemory >>> " + 
                          "oldAddress=" + oldAddress + 
                          ", oldSize=" + oldSize);
  }

  @Override
  public void afterReallocateMemory(long oldAddress, long oldSize, 
                                    long newAddress, long newSize, boolean isKnownAddress) {
    System.out.println("afterReallocateMemory >>> " + 
                          "oldAddress=" + oldAddress + 
                          ", oldSize=" + oldSize +
                          ", newAddress=" + newAddress + 
                          ", newSize=" + newSize +
                          ", isKnownAddress=" + isKnownAddress);
  }
  
};

...

// Register listener to be notified for each allocate/free
MySafe.registerMemoryListener(listener);

...

// Deregister registered listener
MySafe.deregisterMemoryListener(listener);
```

### 5.5. Dumping Allocated Native Memories 

All allocated memories can be dumped via `MySafe.dumpAllocatedMemories()` or `MySafe.dumpAllocatedMemories(PrintStream)` methods.

Here is its sample usage:
``` java
// Dump all allocated memories to console
MySafe.dumpAllocatedMemories();

...

PrintStream myPrintStream = ...
// Dump all allocated memories to `myPrintStream`
MySafe.dumpAllocatedMemories(myPrintStream);
```

### 5.6. AllocationPathManager 

`AllocationPathManager` interface is contract point to manage allocation path & address mappings/un-mappings and allocation path provide related operations. It is specified via `mysafe.allocationPathManagerImpl` system property.


### 5.7. Dumping Allocation Paths 

All unique allocation paths with allocated memories through them can be dumped  via `MySafe.dumpAllocationPaths()` or `MySafe.dumpAllocationPaths(PrintStream)` methods if allocation path monitoring is enabled by `mysafe.enableAllocationPathMonitoringMode` property.

Here is its sample usage:
``` java
// Dump all allocation paths with allocated memories through them to console
MySafe.dumpAllocationPaths();

...

PrintStream myPrintStream = ...
// Dump all allocation paths with allocated memories through them to `myPrintStream`
MySafe.dumpAllocationPaths(myPrintStream);
```

### 5.8. Generating Allocation Path Diagram 

All unique allocation paths with allocated memories through them can be dumped via `MySafe.generateAllocationPathDiagrams()` method if allocation path monitoring is enabled by `mysafe.enableAllocationPathMonitoringMode` property.

Here is its sample usage:
``` java
// Generate allocation path diagram
MySafe.generateAllocationPathDiagrams();
```

## 6. Demo

[Here](https://github.com/serkan-ozal/mysafe/blob/master/src/test/java/tr/com/serkanozal/mysafe/MemoryExplorerDemo.java) is demo application for demonstrating how to iterate on allocated memories and dump them.

[Here](https://github.com/serkan-ozal/mysafe/blob/master/src/test/java/tr/com/serkanozal/mysafe/MemoryListenerDemo.java) is demo application for demonstrating how to track memory allocation, reallocation and free operations.

[Here](https://github.com/serkan-ozal/mysafe/blob/master/src/test/java/tr/com/serkanozal/mysafe/IllegalMemoryAccessListenerDemo.java) is demo application for demonstrating how to be notified on illegal memory accesses.

[Here](https://github.com/serkan-ozal/mysafe/blob/master/src/test/java/tr/com/serkanozal/mysafe/CustomMemoryManagementDemo.java) is demo application for demonstrating how to integrate **MySafe** with custom memory managers.

[Here](https://github.com/serkan-ozal/mysafe/blob/master/src/test/java/tr/com/serkanozal/mysafe/NativeMemoryLeakHuntingDemo.java) is demo application for demonstrating how to hunt native memory leaks via **MySafe**.

Here is the generated allocation path diagram for the `NativeMemoryLeakHuntingDemo` which shows the cause of native memory leak:
![native-memory-leak-hunting](https://github.com/serkan-ozal/mysafe/blob/master/src/test/resources/native-memory-leak-hunting.png) 

## 7. Fixes & Enhancements

Bug fixes and enhancements at each release:

### 7.1. Version 1.1

* Introduced `UnsafeMemoryAccessor` for memory access abstraction through `Unsafe`. Also introduced `AlignmentAwareUnsafeMemoryAccessor` to support unaligned memory accesses on platforms which don't support unaligned memory accesses such as **SPARC**.

### 7.2. Version 2.0

* Some renaming on interfaces, classes and method names about **Unsafe** terms including API.
* Ability to specify custom memory allocation, reallocation and free points (methods) instead of `Unsafe`'s `allocateMemory`, `freeMemory` and `reallocateMemory` methods.
* Ability to monitor stacktraces of memory allocations by **class name** and **method name** (or **constructor**/**class initializer**).
* Ability to storing allocated memory addresses and allocation path informations (if enabled) at off-heap instead of heap.
* Ability to generate allocation path diagrams.

### 7.3. Version 2.1

* Fixed [NoSuchMethodError: MySafeDelegator.compareAndSwapLong](https://github.com/serkan-ozal/mysafe/issues/2) issue.
* Introduced `AllocationPathManager` API.

## 8. Roadmap

* Ability to track also **line numbers** for stacktraces of memory allocations.
* More detailed and accurate allocation path detection.
* Ability to inspect directly `sun.misc.Unsafe` instead of application classes which uses `sun.misc.Unsafe`.
* Java 9 support.
* Allocation path detection via Java 9’s **StackWalker** API.
* Flame graph support.

