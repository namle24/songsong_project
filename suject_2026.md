# SongSong Project

## Implementing a Parallel Download Infrastructure for Large Files

## Context and Objectives

The goal of this project is to design and implement a distributed system that enables parallel file
downloading from multiple sources. This approach aims to improve download performance by
leveraging network parallelism.

## General Principles

- A client can download a file in parallel from multiple sources (other clients). Different parts
    of the file will be downloaded in parallel from different sources.
- A central **directory** keeps track of files available on connected clients. This directory is kept
    uptodate whenever a client connects or disconnects.
- A file is identified solely by its name. We assume that each file name is unique and identifies
    the same file on clients where the file exists.
- When a client wants to download a file, the directory provides the list of clients that own it.

## General Architecture

The architecture consists of three main components:

1. **Directory**
    - Executes on one particular node.
    - Stores the list of files available on connected clients, and for each file, the list of
       clients from which the file can be donwloaded.
    - Accessible via **RMI**.
2. **Daemon**
    - Executes on each client.
    - At startup, registers in Directory the files owned by the client.
    - Enables the downloading of file fragments.
    - Communicates with downloading clients via **TCP sockets**.
3. **Download**
    - Allows the download of a file in parallel by retrieving fragments from multiple
       clients.
    - Communicates with providing clients via **TCP sockets**.


Here is an overview of the architecture.

Launching the application :

- on the directory node, the Directory RMI server can be launched with : _java Directory_
- on each client node, the Daemon socket server can be launched with : _java Daemon_
- on one client node, a downloading can be started with : _java Download <filename>_

When Daemon is started on a client node, it informs (1) the Directory about the available files on
the client node (the files in a specific folder). When a file download starts, the Download
component requests the Directory (2) to get the list of clients where the file is available, and it then
downloads (3) in parallel different fragments of the file from different clients.

## Expected Outcomes

- **Basic Prototype**
    - Build a prototype of this system (including the described components).
- **Parallelism Validation** :
    - Measure the performance improvement when increasing the number of sources for a
       given download. You should draw a curve.
    - Compare the performance gain against a sequential download.
    - NB : to observe real improvements, you need to experiment with a slow network
       (e.g. the Internet, not with a LAN). You can experiment between machines at USTH
       and your home, or allocate virtual machines from a cloud provider on the other side
       of the earth.
- **Enhancements** :
    - **Failure and disconnection handling** :
       - During download of a fragment from a client, the client may disconnect (or
          fail). Then, the download of that fragment should be automatically resumed
          from another available fragment.
    - **Dynamic adaptation** :
       - Detect and integrate newly available clients (in the Directory)
       - Detect and take into account disconnected clients (in the Directory)


- Optimize source (client) selection based on the load on clients (e.g. the
    number of downloads in progress from clients).
- **Data compression** :
- Experiment with compressed files to improve download speeds.

## Project Evaluation

- The project should be done in pairs (2 students)
- **Basic Prototype** and **Parallelism Validation** are mandatory.
- **Enhancements** are possible improvements. You should consider doing 1 or 2 among these
    3.
- You must return a **report** (less than 5 pages) which describes the manual (how I can run
    your system on my laptop) and your achievements (functionalities and performance figures).
- You must return an archive (tgz format) with your source code (only source code, please
    remove large files, such an archive should not exceed 500KB).
- The report and code archive must be submitted via email to _daniel.hagimont@irit.fr_ before
    March 18st (noon french time).
- We will organize video-conferences where you will have to prensent your project and
    explain your contribution.


