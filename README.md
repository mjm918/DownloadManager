# DownloadManager

Pure java download manager. Multithreaded asynchronize example of downloading files. Faster than normal chrome download. 
Same logic and algorithm as Internet Download Manager.

# Limitation
Not able to download from URI. Example: www.example.com/download/file

# Advantages
Any link containing exact file path (Ex: www.example.com/download/file.zip) is downloadable. Able to download any kind of files.
Tested in real environment and proven.

# Method
Logic behind downloading the files is:

>> Check link with http request whether the link is valid.
>> Get length of the file content.
>> Split the content into 8 worker threads with synchronized functions.
>> Threads will run the file content writing function synchronizingly.
>> Each of the threads will save the files in chunks in temp names. So there will be 8 temp files which will be merged later.
>> InputStream length will be 8Kb. Once a thread has finished writing a chunk will wait for other threads to be finished as
well.
>> Upon notified from all the threads, program will merge all the files together.


---------------------------------------------------------------------------------

# Reason
Normal download process assign one process or thread to download the whole file. It takes more time than usual. Splitting 
the file into chunks and then assign worker threads to download them individually makes the download faster. Since all the
worker threads are synchronized, it doesn't interrupt the writting process.

# Inspired by 
* Internet Download Manager

# Supports
* Stackoverflow (obviously :P)
* Some other authors (Forgot their names :P)
