default: STM2IR.class

STM2IR.class: STM2IR.java
	javac STM2IR.java

run: STM2IR.class
	java STM2IR ${ARGS}

clean:
	rm *.class