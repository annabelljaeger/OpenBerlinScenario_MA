import csv

# Input and output file paths
input_file = 'C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legsLostTimeModespecificTest2.csv'
output_file = 'C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legsLostTimeModespecificTest2_converted.csv'

# Open the input file in read mode and the output file in write mode
with open(input_file, mode='r', newline='', encoding='utf-8') as infile, \
     open(output_file, mode='w', newline='', encoding='utf-8') as outfile:

    reader = csv.reader(infile)
    writer = csv.writer(outfile, delimiter=';')

    # Write each row from the input file to the output with a semicolon delimiter
    for row in reader:
        writer.writerow(row)

print("Conversion complete!")