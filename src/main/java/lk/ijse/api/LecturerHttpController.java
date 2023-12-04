package lk.ijse.api;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lecturers")
@CrossOrigin
public class LecturerHttpController {
    @PostMapping
 public void  createNewLecturer(){
        System.out.println("createNew Lecturer()");
 }

    @PatchMapping("/{lecturer-id")
    public  void updateLecturerDetails(){
        System.out.println("UpdateLecturersDetails()");
 }

 @DeleteMapping("/{lecturer-id}")
    public  void deleteLecturer(){
     System.out.println("DeleteLecturer()");
 }

 @GetMapping
    public  void getAllLecturers(){
     System.out.println("getAllLecturers()");
 }


}
