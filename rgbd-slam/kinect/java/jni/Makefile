CXX = g++ -Wall -g
KINECT_OBJS = Kinect.o

LIB=libkinect.so
DESTLIB=../../lib/$(LIB)

.SUFFIXES: .java

all: $(DESTLIB)

$(DESTLIB): $(LIB)
	cp $(LIB) $(DESTLIB)

$(LIB): kinect_Kinect.h $(KINECT_OBJS)

kinect_Kinect.h: ../src/kinect/Kinect.java
	echo "Rebuilding JNI headers. Ensure java file has been recently built."
	javah -classpath ../kinect.jar kinect.Kinect

clean:
	rm -f $(KINECT_OBJS) *~ kinect_Kinect.h *.so $(LIB) $(DESTLIB)

JNI_INCLUDES = -I/usr/lib/jvm/java-6-sun/include/ -I/usr/lib/jvm/java-6-sun/include/linux -I/usr/lib/jvm/java-6-openjdk/include/ -I/System/Library/Frameworks/JavaVM.framework/Headers
KINECT_INCLUDES = -I$(HOME)/Documents/schoolwork/E568/libfreenect/include -I$(HOME)/libfreenect/include -I/usr/local/include/libfreenect
KINECT_LIBS = -L$(HOME)/Documents/schoolwork/E568/libfreenect/build/lib -L$(HOME)/libfreenect/build/lib -lfreenect -lpthread

%.o: %.cpp
	$(CXX) -shared -O2 -fPIC $< $(JNI_INCLUDES) $(KINECT_INCLUDES) $(KINECT_LIBS) -o $(LIB)


