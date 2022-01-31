package com.pugwoo.wooutils;

import com.pugwoo.wooutils.fortest.EqualUtils;
import com.pugwoo.wooutils.json.JSON;
import com.pugwoo.wooutils.lang.DateUtils;
import com.rits.cloning.Cloner;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class TestFastClone {

    public static class StudentDTO {
        private Long id;
        private String name;
        private Boolean valid;
        private Date birth;

        public Long getId() {
            return id;
        }
        public void setId(Long id) {
            this.id = id;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public Boolean getValid() {
            return valid;
        }
        public void setValid(Boolean valid) {
            this.valid = valid;
        }
        public Date getBirth() {
            return birth;
        }
        public void setBirth(Date birth) {
            this.birth = birth;
        }
    }

    @Test
    public void test() {
        List<StudentDTO> list = generate(100000);

        long start = System.currentTimeMillis();

        List<StudentDTO> cloned = JSON.clone(list, StudentDTO.class);

        long end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms"); // 1536ms

        EqualUtils equalUtils = new EqualUtils();
        assert equalUtils.isEqual(list, cloned);

        Cloner cloner = new Cloner();

        start = System.currentTimeMillis();

        List<StudentDTO> cloned2 = cloner.deepClone(list);
        end = System.currentTimeMillis();

        System.out.println("cost:" + (end - start) + "ms");

        assert equalUtils.isEqual(list, cloned2);
    }

    private List<StudentDTO> generate(int num) {
        List<StudentDTO> list = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            StudentDTO studentDTO = new StudentDTO();
            studentDTO.setId(new Random().nextLong());
            studentDTO.setName(UUID.randomUUID().toString());
            studentDTO.setValid(new Random().nextBoolean());
            studentDTO.setBirth(DateUtils.parse(DateUtils.format(new Date())));

            list.add(studentDTO);
        }

        return list;
    }

}
