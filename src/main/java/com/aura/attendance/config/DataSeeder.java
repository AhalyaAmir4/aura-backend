package com.aura.attendance.config;

import com.aura.attendance.entity.Subject;
import com.aura.attendance.entity.User;
import com.aura.attendance.repository.SubjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds all standard subjects for ECE, CSE, IT, AIDS, MECH, CIVIL, EEE
 * for Years 1-4 on first startup. Skips if code already exists.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final SubjectRepository subjectRepository;

    public DataSeeder(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Override
    public void run(String... args) {
        List<SubjectSeed> seeds = buildSeeds();
        int added = 0;
        for (SubjectSeed s : seeds) {
            boolean exists = subjectRepository.findAllByOrderByDepartmentAscYearAsc()
                    .stream().anyMatch(sub -> sub.getCode().equalsIgnoreCase(s.code));
            if (!exists) {
                subjectRepository.save(Subject.builder()
                        .name(s.name).code(s.code)
                        .credits(s.credits).department(s.dept).year(s.year).build());
                added++;
            }
        }
        if (added > 0) System.out.println("[AURA] Seeded " + added + " subjects.");
    }

    private List<SubjectSeed> buildSeeds() {
        List<SubjectSeed> seeds = new ArrayList<>();

        // ── YEAR 1 — Common for all depts ──
        for (User.Department dept : User.Department.values()) {
            String d = dept.name();
            seeds.add(new SubjectSeed("Engineering Mathematics I",   d+"101MA", 4, dept, 1));
            seeds.add(new SubjectSeed("Engineering Physics",         d+"102PH", 3, dept, 1));
            seeds.add(new SubjectSeed("Engineering Chemistry",       d+"103CH", 3, dept, 1));
            seeds.add(new SubjectSeed("English for Communication",   d+"104EN", 3, dept, 1));
            seeds.add(new SubjectSeed("Engineering Graphics",        d+"105EG", 4, dept, 1));
            seeds.add(new SubjectSeed("Basic Electrical Engineering",d+"106BE", 3, dept, 1));
        }

        // ── ECE ──
        seeds.add(new SubjectSeed("Engineering Mathematics II",        "ECE201MA", 4, User.Department.ECE, 2));
        seeds.add(new SubjectSeed("Electronic Devices & Circuits",     "ECE202ED", 3, User.Department.ECE, 2));
        seeds.add(new SubjectSeed("Signals & Systems",                 "ECE203SS", 3, User.Department.ECE, 2));
        seeds.add(new SubjectSeed("Digital Electronics",               "ECE204DE", 3, User.Department.ECE, 2));
        seeds.add(new SubjectSeed("Circuit Theory",                    "ECE205CT", 3, User.Department.ECE, 2));
        seeds.add(new SubjectSeed("Analog Circuits",                   "ECE301AC", 3, User.Department.ECE, 3));
        seeds.add(new SubjectSeed("Communication Theory",              "ECE302CM", 3, User.Department.ECE, 3));
        seeds.add(new SubjectSeed("Microprocessors & Microcontrollers","ECE303MP", 3, User.Department.ECE, 3));
        seeds.add(new SubjectSeed("VLSI Design",                       "ECE304VL", 3, User.Department.ECE, 3));
        seeds.add(new SubjectSeed("Embedded Systems",                  "ECE401ES", 3, User.Department.ECE, 4));
        seeds.add(new SubjectSeed("Wireless Communication",            "ECE402WC", 3, User.Department.ECE, 4));
        seeds.add(new SubjectSeed("Digital Signal Processing",         "ECE403DS", 3, User.Department.ECE, 4));
        seeds.add(new SubjectSeed("Antenna & Wave Propagation",        "ECE404AW", 3, User.Department.ECE, 4));
        seeds.add(new SubjectSeed("Project Work",                      "ECE405PW", 6, User.Department.ECE, 4));

        // ── CSE ──
        seeds.add(new SubjectSeed("Data Structures",                   "CSE201DS", 4, User.Department.CSE, 2));
        seeds.add(new SubjectSeed("Object Oriented Programming",       "CSE202OO", 3, User.Department.CSE, 2));
        seeds.add(new SubjectSeed("Discrete Mathematics",              "CSE203DM", 3, User.Department.CSE, 2));
        seeds.add(new SubjectSeed("Database Management Systems",       "CSE204DB", 3, User.Department.CSE, 2));
        seeds.add(new SubjectSeed("Computer Organization",             "CSE205CO", 3, User.Department.CSE, 2));
        seeds.add(new SubjectSeed("Design & Analysis of Algorithms",   "CSE301DA", 4, User.Department.CSE, 3));
        seeds.add(new SubjectSeed("Operating Systems",                 "CSE302OS", 3, User.Department.CSE, 3));
        seeds.add(new SubjectSeed("Computer Networks",                 "CSE303CN", 3, User.Department.CSE, 3));
        seeds.add(new SubjectSeed("Software Engineering",              "CSE304SE", 3, User.Department.CSE, 3));
        seeds.add(new SubjectSeed("Compiler Design",                   "CSE305CD", 3, User.Department.CSE, 3));
        seeds.add(new SubjectSeed("Machine Learning",                  "CSE401ML", 3, User.Department.CSE, 4));
        seeds.add(new SubjectSeed("Cloud Computing",                   "CSE402CC", 3, User.Department.CSE, 4));
        seeds.add(new SubjectSeed("Cyber Security",                    "CSE403CS", 3, User.Department.CSE, 4));
        seeds.add(new SubjectSeed("Deep Learning",                     "CSE404DL", 3, User.Department.CSE, 4));
        seeds.add(new SubjectSeed("Project Work",                      "CSE405PW", 6, User.Department.CSE, 4));

        // ── IT ──
        seeds.add(new SubjectSeed("Data Structures",                   "IT201DS",  4, User.Department.IT,  2));
        seeds.add(new SubjectSeed("Web Technologies",                  "IT202WT",  3, User.Department.IT,  2));
        seeds.add(new SubjectSeed("Database Systems",                  "IT203DB",  3, User.Department.IT,  2));
        seeds.add(new SubjectSeed("Computer Networks",                 "IT204CN",  3, User.Department.IT,  2));
        seeds.add(new SubjectSeed("Python Programming",                "IT205PY",  3, User.Department.IT,  2));
        seeds.add(new SubjectSeed("Software Engineering",              "IT301SE",  3, User.Department.IT,  3));
        seeds.add(new SubjectSeed("Operating Systems",                 "IT302OS",  3, User.Department.IT,  3));
        seeds.add(new SubjectSeed("Information Security",              "IT303IS",  3, User.Department.IT,  3));
        seeds.add(new SubjectSeed("Mobile Application Development",    "IT304MD",  3, User.Department.IT,  3));
        seeds.add(new SubjectSeed("Cloud & IoT",                       "IT401CI",  3, User.Department.IT,  4));
        seeds.add(new SubjectSeed("Data Analytics",                    "IT402DA",  3, User.Department.IT,  4));
        seeds.add(new SubjectSeed("Full Stack Development",            "IT403FS",  3, User.Department.IT,  4));
        seeds.add(new SubjectSeed("Project Work",                      "IT404PW",  6, User.Department.IT,  4));

        // ── AIDS ──
        seeds.add(new SubjectSeed("Statistics & Probability",          "AIDS201SP", 4, User.Department.AIDS, 2));
        seeds.add(new SubjectSeed("Data Structures",                   "AIDS202DS", 3, User.Department.AIDS, 2));
        seeds.add(new SubjectSeed("Python for AI",                     "AIDS203PY", 3, User.Department.AIDS, 2));
        seeds.add(new SubjectSeed("Database Management",               "AIDS204DB", 3, User.Department.AIDS, 2));
        seeds.add(new SubjectSeed("Machine Learning",                  "AIDS301ML", 4, User.Department.AIDS, 3));
        seeds.add(new SubjectSeed("Deep Learning",                     "AIDS302DL", 3, User.Department.AIDS, 3));
        seeds.add(new SubjectSeed("Natural Language Processing",       "AIDS303NL", 3, User.Department.AIDS, 3));
        seeds.add(new SubjectSeed("Computer Vision",                   "AIDS304CV", 3, User.Department.AIDS, 3));
        seeds.add(new SubjectSeed("Big Data Analytics",                "AIDS401BD", 3, User.Department.AIDS, 4));
        seeds.add(new SubjectSeed("Generative AI",                     "AIDS402GA", 3, User.Department.AIDS, 4));
        seeds.add(new SubjectSeed("AI Ethics & Governance",            "AIDS403AE", 3, User.Department.AIDS, 4));
        seeds.add(new SubjectSeed("Project Work",                      "AIDS404PW", 6, User.Department.AIDS, 4));

        // ── MECH ──
        seeds.add(new SubjectSeed("Engineering Mechanics",             "MECH201EM", 4, User.Department.MECH, 2));
        seeds.add(new SubjectSeed("Thermodynamics",                    "MECH202TH", 3, User.Department.MECH, 2));
        seeds.add(new SubjectSeed("Material Science",                  "MECH203MS", 3, User.Department.MECH, 2));
        seeds.add(new SubjectSeed("Manufacturing Technology",          "MECH204MT", 3, User.Department.MECH, 2));
        seeds.add(new SubjectSeed("Fluid Mechanics",                   "MECH301FM", 4, User.Department.MECH, 3));
        seeds.add(new SubjectSeed("Heat Transfer",                     "MECH302HT", 3, User.Department.MECH, 3));
        seeds.add(new SubjectSeed("Machine Design",                    "MECH303MD", 3, User.Department.MECH, 3));
        seeds.add(new SubjectSeed("CAD/CAM",                          "MECH304CA", 3, User.Department.MECH, 3));
        seeds.add(new SubjectSeed("Automobile Engineering",            "MECH401AE", 3, User.Department.MECH, 4));
        seeds.add(new SubjectSeed("Robotics",                          "MECH402RO", 3, User.Department.MECH, 4));
        seeds.add(new SubjectSeed("Project Work",                      "MECH403PW", 6, User.Department.MECH, 4));

        // ── CIVIL ──
        seeds.add(new SubjectSeed("Structural Analysis",               "CIVIL201SA", 4, User.Department.CIVIL, 2));
        seeds.add(new SubjectSeed("Fluid Mechanics",                   "CIVIL202FM", 3, User.Department.CIVIL, 2));
        seeds.add(new SubjectSeed("Soil Mechanics",                    "CIVIL203SM", 3, User.Department.CIVIL, 2));
        seeds.add(new SubjectSeed("Surveying",                         "CIVIL204SU", 3, User.Department.CIVIL, 2));
        seeds.add(new SubjectSeed("Concrete Technology",               "CIVIL301CT", 3, User.Department.CIVIL, 3));
        seeds.add(new SubjectSeed("Transportation Engineering",        "CIVIL302TE", 3, User.Department.CIVIL, 3));
        seeds.add(new SubjectSeed("Environmental Engineering",         "CIVIL303EE", 3, User.Department.CIVIL, 3));
        seeds.add(new SubjectSeed("Foundation Engineering",            "CIVIL401FE", 3, User.Department.CIVIL, 4));
        seeds.add(new SubjectSeed("Project Work",                      "CIVIL402PW", 6, User.Department.CIVIL, 4));

        // ── EEE ──
        seeds.add(new SubjectSeed("Electrical Machines I",             "EEE201M1", 4, User.Department.EEE, 2));
        seeds.add(new SubjectSeed("Circuit Analysis",                  "EEE202CA", 3, User.Department.EEE, 2));
        seeds.add(new SubjectSeed("Electromagnetic Theory",            "EEE203EM", 3, User.Department.EEE, 2));
        seeds.add(new SubjectSeed("Power Systems I",                   "EEE204PS", 3, User.Department.EEE, 2));
        seeds.add(new SubjectSeed("Electrical Machines II",            "EEE301M2", 4, User.Department.EEE, 3));
        seeds.add(new SubjectSeed("Power Electronics",                 "EEE302PE", 3, User.Department.EEE, 3));
        seeds.add(new SubjectSeed("Control Systems",                   "EEE303CS", 3, User.Department.EEE, 3));
        seeds.add(new SubjectSeed("Power Systems II",                  "EEE304P2", 3, User.Department.EEE, 3));
        seeds.add(new SubjectSeed("High Voltage Engineering",          "EEE401HV", 3, User.Department.EEE, 4));
        seeds.add(new SubjectSeed("Renewable Energy Systems",          "EEE402RE", 3, User.Department.EEE, 4));
        seeds.add(new SubjectSeed("Project Work",                      "EEE403PW", 6, User.Department.EEE, 4));

        return seeds;
    }

    private record SubjectSeed(String name, String code, int credits, User.Department dept, int year) {}
}
