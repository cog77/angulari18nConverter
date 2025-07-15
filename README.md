#Angular i8n Converter

This program has been written for a problem that I found myself in.  
Having translated content for my angular application I was expecting 
to be able to build the application and content would be targetted 
based on the target xlf file.  Instead the application creates folders 
within the application that point to locale specific folders.
As usual this is really bad for SEO and looks like the users are second
 class citizens if not using the root domain.

So this application takes the xlf file that you have populated and injects
the content into a copy of the angular application as the root.  As this is
 a program that can be repeated (as long as you aren't changing the copy 
 inbetween) it can be used within the build process.

Have fun and I hope this is useful (and also is solved for in upcoming versions 
of Angular ;) ).

Mike Cogan

P.S.  The code does need refactoring (please don't start trying to use regular 
expressions everywhere as they are very tricky to debug).  I wrote this 
in a panic scenario on finding that the angular output was in folders as 
mentioned above.  I personally found this way of structuring far easier to step 
through with debug but it could do with some tidying up, refactoring and maybe 
some comments on the more complex searches or assumptions.
